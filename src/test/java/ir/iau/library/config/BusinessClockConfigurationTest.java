package ir.iau.library.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessClockConfigurationTest {
    @Test
    void configuredZoneControlsTheBusinessDateAtMidnightBoundary() {
        var clock = new BusinessClockConfiguration().businessClock("Asia/Tehran");

        assertThat(clock.getZone().getId()).isEqualTo("Asia/Tehran");
        Clock boundary = Clock.fixed(Instant.parse("2026-10-12T20:45:00Z"), clock.getZone());
        assertThat(LocalDate.now(boundary)).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    void blankZoneKeepsTheHostZoneAsTheExplicitDeploymentFallback() {
        var clock = new BusinessClockConfiguration().businessClock(" ");

        assertThat(clock.getZone()).isEqualTo(java.time.ZoneId.systemDefault());
    }
}
