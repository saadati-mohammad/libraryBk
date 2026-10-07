package ir.iau.library.service;

import ir.iau.library.dto.BookLoanDto;
import ir.iau.library.dto.CreateLoanRequestDto;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.entity.LoanStatus;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookLoanRepository;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the loan business rules. These are the highest-value rules in the
 * system: an inactive member must not borrow, and a book already on loan must not be
 * lent twice.
 */
@ExtendWith(MockitoExtension.class)
class BookLoanServiceTest {

    @Mock
    private BookLoanRepository loanRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookLoanService service;

    private Person activePerson;
    private Book book;

    @BeforeEach
    void setUp() {
        activePerson = Person.builder()
                .id(1L)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .nationalId("1234567890")
                .active(true)
                .build();
        book = Book.builder()
                .id(2L)
                .title("Notes on the Analytical Engine")
                .active(true)
                .build();
    }

    @Test
    void createLoan_savesLoanWithDueDateAndOnLoanStatus() {
        CreateLoanRequestDto request = new CreateLoanRequestDto();
        request.setPersonId(1L);
        request.setBookId(2L);
        request.setNotes("first edition");

        when(personRepository.findById(1L)).thenReturn(Optional.of(activePerson));
        // createLoan now takes a pessimistic lock on the book, so it calls findByIdForUpdate.
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(book));
        when(loanRepository.findActiveLoanByBook(book)).thenReturn(Optional.empty());
        when(loanRepository.save(any(BookLoan.class))).thenAnswer(inv -> inv.getArgument(0));

        BookLoanDto result = service.createLoan(request);

        ArgumentCaptor<BookLoan> captor = ArgumentCaptor.forClass(BookLoan.class);
        verify(loanRepository).save(captor.capture());
        BookLoan saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(LoanStatus.ON_LOAN);
        assertThat(saved.getLoanDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(saved.getReturnDate()).isNull();
        assertThat(saved.getNotes()).isEqualTo("first edition");

        assertThat(result.getPersonId()).isEqualTo(1L);
        assertThat(result.getBookId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo(LoanStatus.ON_LOAN);
    }

    @Test
    void createLoan_rejectsInactivePerson() {
        Person inactive = Person.builder()
                .id(1L)
                .email("ada@example.com")
                .nationalId("1234567890")
                .active(false)
                .build();

        CreateLoanRequestDto request = new CreateLoanRequestDto();
        request.setPersonId(1L);
        request.setBookId(2L);

        when(personRepository.findById(1L)).thenReturn(Optional.of(inactive));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.createLoan(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");

        verify(loanRepository, never()).save(any());
    }

    @Test
    void createLoan_rejectsBookAlreadyOnLoan() {
        CreateLoanRequestDto request = new CreateLoanRequestDto();
        request.setPersonId(1L);
        request.setBookId(2L);

        when(personRepository.findById(1L)).thenReturn(Optional.of(activePerson));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(book));
        when(loanRepository.findActiveLoanByBook(book))
                .thenReturn(Optional.of(BookLoan.builder().id(9L).status(LoanStatus.ON_LOAN).build()));

        assertThatThrownBy(() -> service.createLoan(request))
                .isInstanceOf(IllegalStateException.class);

        verify(loanRepository, never()).save(any());
    }

    @Test
    void createLoan_throwsWhenPersonMissing() {
        CreateLoanRequestDto request = new CreateLoanRequestDto();
        request.setPersonId(99L);
        request.setBookId(2L);

        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLoan(request))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
