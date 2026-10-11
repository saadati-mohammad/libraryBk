package ir.iau.library.repository;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.entity.LoanStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;


@Repository
public interface BookLoanRepository extends JpaRepository<BookLoan, Long>, JpaSpecificationExecutor<BookLoan> {
    @EntityGraph(attributePaths = "book")
    Page<BookLoan> findByPersonIdOrderByLoanDateDescIdDesc(Long personId, Pageable pageable);
    /** Number of copies of this title currently out, including overdue loans. */
    @Query("SELECT COUNT(bl) FROM BookLoan bl WHERE bl.book = :book AND bl.status IN ('ON_LOAN', 'OVERDUE')")
    long countActiveLoansByBook(@Param("book") Book book);

    /**
     * Loans still marked {@code ON_LOAN} whose due date has passed. Used by the daily
     * overdue job to flip them to {@code OVERDUE}. A loan due today is not yet overdue,
     * hence {@code dueDate < today}.
     */
    List<BookLoan> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);

    /** Loans that are currently out: on loan or already flagged overdue. */
    @Query("SELECT COUNT(bl) FROM BookLoan bl WHERE bl.status = 'ON_LOAN' OR bl.status = 'OVERDUE'")
    long countActiveLoans();

    /** Loans in a given state (used by the dashboard for the overdue count). */
    long countByStatus(LoanStatus status);

    /**
     * The newest loans with their member and book joined, for the dashboard. Bounded by the
     * caller's {@link Pageable} so the whole table is never materialized.
     */
    @Query("SELECT bl FROM BookLoan bl JOIN FETCH bl.person JOIN FETCH bl.book ORDER BY bl.id DESC")
    List<BookLoan> findRecentLoans(Pageable pageable);

    /**
     * Loan counts grouped by calendar month from {@code from} onwards, as
     * {@code [year, month, count]} rows. YEAR/MONTH are standard JPQL functions and work on
     * both H2 and MySQL.
     */
    @Query("SELECT YEAR(bl.loanDate), MONTH(bl.loanDate), COUNT(bl) FROM BookLoan bl " +
            "WHERE bl.loanDate >= :from " +
            "GROUP BY YEAR(bl.loanDate), MONTH(bl.loanDate) " +
            "ORDER BY YEAR(bl.loanDate), MONTH(bl.loanDate)")
    List<Object[]> countLoansByMonth(@Param("from") LocalDate from);
}
