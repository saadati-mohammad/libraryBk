package ir.iau.library.repository;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.entity.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


@Repository
public interface BookLoanRepository extends JpaRepository<BookLoan, Long>, JpaSpecificationExecutor<BookLoan> {
    // برای بررسی اینکه آیا کتابی در حال حاضر در امانت (یا دیرکرد) است یا خیر
    @Query("SELECT bl FROM BookLoan bl WHERE bl.book = :book AND (bl.status = 'ON_LOAN' OR bl.status = 'OVERDUE')")
    Optional<BookLoan> findActiveLoanByBook(Book book);

    /**
     * Loans still marked {@code ON_LOAN} whose due date has passed. Used by the daily
     * overdue job to flip them to {@code OVERDUE}. A loan due today is not yet overdue,
     * hence {@code dueDate < today}.
     */
    List<BookLoan> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);
}