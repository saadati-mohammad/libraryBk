package ir.iau.library.controller;

import ir.iau.library.dto.DashboardSummaryDto;
import ir.iau.library.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard aggregates for the operator UI.
 *
 * <p>Returns a plain JSON object (like the other list endpoints) rather than the
 * {@code ApiResponse} envelope, so the dashboard can bind the fields directly.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "خلاصه داشبورد",
            description = "تعداد کتاب‌ها، اعضا، امانت‌های فعال و دیرکرد، رزروهای فعال و روند ۶ ماهه")
    public DashboardSummaryDto summary() {
        return dashboardService.getSummary();
    }
}
