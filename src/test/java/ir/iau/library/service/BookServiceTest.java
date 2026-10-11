package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.entity.LoanStatus;
import ir.iau.library.repository.BookRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void deleteBookById_softDeletesInsteadOfRemovingRow() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("کتاب آزمون");
        book.setActive(true);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        bookService.deleteBookById(1L);

        // The row must NOT be hard-deleted — that would cascade-delete loan history.
        verify(bookRepository, never()).deleteById(any());
        verify(bookRepository, never()).delete(any(Book.class));

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }

    /**
     * A book that still has loan history (or a reservation) is exactly the case a hard delete
     * broke: {@code cascade = ALL, orphanRemoval = true} wiped the loans, and the reservation
     * FK made the delete fail with a 500. Deactivation must leave both untouched.
     */
    @Test
    void deleteBookById_softDeletesBookThatStillHasLoanAndReservationHistory() {
        Book book = new Book();
        book.setId(7L);
        book.setActive(true);
        BookLoan existingLoan = BookLoan.builder().id(99L).status(LoanStatus.ON_LOAN).build();
        book.setLoans(new ArrayList<>(List.of(existingLoan)));
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));

        bookService.deleteBookById(7L);

        verify(bookRepository, never()).delete(any(Book.class));
        verify(bookRepository, never()).deleteById(any());

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        Book saved = captor.getValue();
        assertThat(saved.getActive()).isFalse();
        // Loan history survives the delete.
        assertThat(saved.getLoans()).containsExactly(existingLoan);
    }

    @Test
    void deleteBookById_isIdempotentForAnAlreadyInactiveBook() {
        Book book = new Book();
        book.setId(8L);
        book.setActive(false);
        when(bookRepository.findById(8L)).thenReturn(Optional.of(book));

        bookService.deleteBookById(8L);

        assertThat(book.getActive()).isFalse();
        verify(bookRepository).save(book);
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void deleteBookById_throwsWhenBookMissing() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.deleteBookById(404L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(bookRepository, never()).save(any());
        verify(bookRepository, never()).deleteById(any());
    }

    /**
     * A create request that omits {@code active} (the JSON API is not required to send it) used
     * to persist {@code null}. Such a row is invisible in active listings, uncounted on the
     * dashboard and refused by the loan/reservation guards — an unusable record.
     */
    @Test
    void createBook_defaultsActiveToTrueWhenTheClientOmitsTheFlag() throws Exception {
        Book request = new Book();
        request.setTitle("بدون وضعیت");
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book created = bookService.createBook(request, null);

        assertThat(created.getActive()).isTrue();
        assertThat(created.getId()).isNull();
        assertThat(created.getLoans()).isNull();
    }

    @Test
    void createBook_keepsAnExplicitlyInactiveFlag() throws Exception {
        Book request = new Book();
        request.setTitle("غیرفعال");
        request.setActive(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookService.createBook(request, null).getActive()).isFalse();
    }

    /**
     * The update body may legitimately omit {@code active} (e.g. a partial client); it must not
     * silently deactivate the book, while an explicit {@code false} still must.
     */
    @Test
    void updateBook_leavesTheActiveFlagUntouchedWhenTheBodyOmitsIt() throws Exception {
        Book existing = new Book();
        existing.setId(5L);
        existing.setActive(true);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book patch = new Book();
        patch.setTitle("عنوان تازه");

        Book updated = bookService.updateBook(5L, patch, null);

        assertThat(updated.getActive()).isTrue();
    }

    @Test
    void updateBook_appliesAnExplicitDeactivation() throws Exception {
        Book existing = new Book();
        existing.setId(6L);
        existing.setActive(true);
        when(bookRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book patch = new Book();
        patch.setTitle("عنوان تازه");
        patch.setActive(false);

        assertThat(bookService.updateBook(6L, patch, null).getActive()).isFalse();
    }
}
