package ir.iau.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Central business clock so circulation dates are deterministic and use one configured zone. */
@Configuration
public class BusinessClockConfiguration {
    @Bean
    public Clock businessClock(@Value("${app.business-zone:}") String configuredZone) {
        ZoneId zone = configuredZone == null || configuredZone.isBlank()
                ? ZoneId.systemDefault()
                : ZoneId.of(configuredZone.strip());
        return Clock.system(zone);
    }
}
