package ir.iau.library.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read model for {@code GET /api/dashboard/summary}.
 *
 * <p>Every scalar is produced by a database count/aggregate query; the collections are
 * bounded (5 recent loans, 6 months) so the endpoint never materializes a whole table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {

    /** Books that are not soft-deleted. */
    private long totalBooks;
    /** Members that are not deactivated. */
    private long totalPersons;
    /** Loans currently out: {@code ON_LOAN} or {@code OVERDUE}. */
    private long activeLoans;
    /** Loans flagged {@code OVERDUE} by the daily job. */
    private long overdueLoans;
    /** Reservations with status {@code ACTIVE}. */
    private long activeReservations;
    /** The 5 newest loans, newest first. */
    private List<BookLoanDto> recentLoans;
    /** Loan count per month for the last 6 months, oldest first, gaps filled with 0. */
    private List<MonthlyLoanCount> monthlyLoans;

    /** One point of the monthly loan series. {@code month} is an ISO {@code yyyy-MM} string. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyLoanCount {
        private String month;
        private long count;
    }
}
