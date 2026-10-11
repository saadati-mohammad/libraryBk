package ir.iau.library.service;

import ir.iau.library.dto.BookLoanDto;
import ir.iau.library.dto.DashboardSummaryDto;
import ir.iau.library.entity.LoanStatus;
import ir.iau.library.entity.ReservationStatus;
import ir.iau.library.repository.BookLoanRepository;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate figures for the operator dashboard.
 *
 * <p>Every number comes from a {@code COUNT}/{@code GROUP BY} query or a bounded, indexed
 * fetch ({@link BookLoanRepository#findRecentLoans}). Nothing here loads a whole table: the
 * endpoints this replaced would have needed {@code findAll()} plus in-memory grouping, which
 * degrades linearly with the library size.
 *
 * <p>{@code totalBooks}/{@code totalPersons} count non-deactivated rows, because
 * deactivation is this application's soft delete (see {@link BookService#deleteBookById} and
 * {@link PersonService#deactivatePerson}) and a deleted record must not appear on the
 * dashboard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final int RECENT_LOAN_LIMIT = 5;
    private static final int MONTHLY_WINDOW = 6;

    private final BookRepository bookRepository;
    private final PersonRepository personRepository;
    private final BookLoanRepository bookLoanRepository;
    private final ReservationRepository reservationRepository;
    private final BookLoanService bookLoanService;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        List<BookLoanDto> recentLoans = bookLoanRepository
                .findRecentLoans(PageRequest.of(0, RECENT_LOAN_LIMIT))
                .stream()
                // Reuses the single loan->DTO mapping instead of duplicating it.
                .map(bookLoanService::convertToDto)
                .toList();

        DashboardSummaryDto summary = DashboardSummaryDto.builder()
                .totalBooks(bookRepository.countByActiveTrue())
                .totalPersons(personRepository.countByActiveTrue())
                .activeLoans(bookLoanRepository.countActiveLoans())
                .overdueLoans(bookLoanRepository.countByStatus(LoanStatus.OVERDUE))
                .activeReservations(reservationRepository.countByStatus(ReservationStatus.ACTIVE))
                .recentLoans(recentLoans)
                .monthlyLoans(monthlyLoans())
                .build();

        log.debug("Dashboard summary computed: {} books, {} members, {} active loans",
                summary.getTotalBooks(), summary.getTotalPersons(), summary.getActiveLoans());
        return summary;
    }

    /**
     * Loans per month for the last {@value #MONTHLY_WINDOW} months, oldest first. Months
     * with no loans are returned with a count of {@code 0} so the client can render a
     * continuous series without filling gaps itself.
     */
    private List<DashboardSummaryDto.MonthlyLoanCount> monthlyLoans() {
        YearMonth firstMonth = YearMonth.now().minusMonths(MONTHLY_WINDOW - 1L);
        LocalDate from = firstMonth.atDay(1);

        Map<String, Long> countsByMonth = new HashMap<>();
        for (Object[] row : bookLoanRepository.countLoansByMonth(from)) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            countsByMonth.put(YearMonth.of(year, month).toString(), ((Number) row[2]).longValue());
        }

        List<DashboardSummaryDto.MonthlyLoanCount> series = new ArrayList<>(MONTHLY_WINDOW);
        for (int i = 0; i < MONTHLY_WINDOW; i++) {
            String month = firstMonth.plusMonths(i).toString(); // ISO yyyy-MM
            series.add(DashboardSummaryDto.MonthlyLoanCount.builder()
                    .month(month)
                    .count(countsByMonth.getOrDefault(month, 0L))
                    .build());
        }
        return series;
    }
}
