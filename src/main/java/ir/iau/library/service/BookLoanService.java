package ir.iau.library.service;

import ir.iau.library.dto.BookLoanDto;
import ir.iau.library.dto.BookLoanFilterDto;
import ir.iau.library.dto.CreateLoanRequestDto;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.entity.LoanStatus;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookLoanRepository;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.specification.BookLoanSpecification;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@Slf4j
public class BookLoanService {

    @Autowired
    private BookLoanRepository loanRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private BookRepository bookRepository;

    private static final int LOAN_DURATION_DAYS = 14;

    public BookLoanDto createLoan(CreateLoanRequestDto request) {
        Person person = personRepository.findById(request.getPersonId())
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        // Pessimistic lock on the book so the availability check and the insert below
        // are atomic: two concurrent requests for the same book cannot both succeed.
        Book book = bookRepository.findByIdForUpdate(request.getBookId())
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));

        // Business rule: Person must be active
        if (!person.getActive()) {
            throw new IllegalStateException("Person is not active and cannot borrow books.");
        }

        // Business rule: Book must be available
        loanRepository.findActiveLoanByBook(book).ifPresent(loan -> {
            throw new IllegalStateException("Book is currently on loan and not available.");
        });

        BookLoan newLoan = BookLoan.builder()
                .person(person)
                .book(book)
                .loanDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(LOAN_DURATION_DAYS))
                .status(LoanStatus.ON_LOAN)
                .notes(request.getNotes())
                .build();

        return convertToDto(loanRepository.save(newLoan));
    }

    public BookLoanDto returnBook(Long loanId) {
        BookLoan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException("Loan not found"));

        if (loan.getStatus() == LoanStatus.RETURNED) {
            throw new IllegalStateException("This book has already been returned.");
        }

        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(LocalDate.now());

        return convertToDto(loanRepository.save(loan));
    }

    public Page<BookLoanDto> findAllFiltered(BookLoanFilterDto filter, Pageable pageable) {
        Page<BookLoan> loanPage = loanRepository.findAll(BookLoanSpecification.filter(filter), pageable);
        return loanPage.map(this::convertToDto);
    }

    /**
     * Flips every loan that is still {@code ON_LOAN} past its due date to {@code OVERDUE}
     * and returns the number updated. Until this ran, the {@code OVERDUE} status existed
     * but was never applied, so an overdue loan stayed {@code ON_LOAN} forever and the UI
     * never surfaced it as overdue. Availability is unaffected: the active-loan query
     * already treats {@code ON_LOAN} and {@code OVERDUE} alike.
     */
    public int markOverdueLoans() {
        List<BookLoan> overdue = loanRepository.findByStatusAndDueDateBefore(LoanStatus.ON_LOAN, LocalDate.now());
        overdue.forEach(loan -> loan.setStatus(LoanStatus.OVERDUE));
        loanRepository.saveAll(overdue);
        return overdue.size();
    }

    /**
     * Daily job: update {@code ON_LOAN} loans past their due date to {@code OVERDUE}.
     */
    @Scheduled(cron = "${app.loan.overdue-cron:0 0 2 * * *}")
    public void scheduledMarkOverdue() {
        try {
            int updated = markOverdueLoans();
            if (updated > 0) {
                log.info("Marked {} loan(s) as OVERDUE", updated);
            }
        } catch (Exception e) {
            // Never let a scheduled failure propagate and stop future executions.
            log.error("Scheduled overdue-loan update failed: {}", e.getMessage(), e);
        }
    }

    private BookLoanDto convertToDto(BookLoan loan) {
        return BookLoanDto.builder()
                .id(loan.getId())
                .loanDate(loan.getLoanDate())
                .dueDate(loan.getDueDate())
                .returnDate(loan.getReturnDate())
                .status(loan.getStatus())
                .notes(loan.getNotes())
                .personId(loan.getPerson().getId())
                .personFirstName(loan.getPerson().getFirstName())
                .personLastName(loan.getPerson().getLastName())
                .bookId(loan.getBook().getId())
                .bookTitle(loan.getBook().getTitle())
                .build();
    }
}