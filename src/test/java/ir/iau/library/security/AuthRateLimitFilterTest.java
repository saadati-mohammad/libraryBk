package ir.iau.library.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {
    @Test
    void throttlesLoginByRemoteAddressAndDoesNotTrustForwardedIpHeader() throws Exception {
        var filter = new AuthRateLimitFilter(new ObjectMapper(), 1, Duration.ofHours(1), 1, Duration.ofHours(1), 1, Duration.ofMinutes(1));
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();

        var firstRequest = request("/api/auth/login");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1");
        filter.doFilter(firstRequest, new MockHttpServletResponse(), chain);

        var blockedResponse = new MockHttpServletResponse();
        var secondRequest = request("/api/auth/login");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.9");
        filter.doFilter(secondRequest, blockedResponse, chain);

        assertThat(passed).hasValue(1);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void limitsPasswordRecoverySeparatelyFromLogin() throws Exception {
        var filter = new AuthRateLimitFilter(new ObjectMapper(), 1, Duration.ofHours(1), 1, Duration.ofHours(1), 1, Duration.ofMinutes(1));
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();

        filter.doFilter(request("/api/auth/forgot-password"), new MockHttpServletResponse(), chain);
        var blockedResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/forgot-password"), blockedResponse, chain);

        assertThat(passed).hasValue(1);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void throttlesInvalidDeviceHeartbeatCredentialsBeforeDatabaseWork() throws Exception {
        var filter = new AuthRateLimitFilter(new ObjectMapper(), 10, Duration.ofHours(1), 10, Duration.ofHours(1), 1, Duration.ofMinutes(1));
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();

        filter.doFilter(request("/api/device-inputs/42/heartbeat"), new MockHttpServletResponse(), chain);
        var blockedResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/device-inputs/42/heartbeat"), blockedResponse, chain);

        assertThat(passed).hasValue(1);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void throttlesPhoneScanSubmissionAttemptsBeforeSessionAndDeviceLookups() throws Exception {
        var filter = new AuthRateLimitFilter(new ObjectMapper(), 10, Duration.ofHours(1), 10, Duration.ofHours(1), 1, Duration.ofMinutes(1));
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();
        String path = "/api/phone-scanner/sessions/3b241101-e2bb-4255-8caf-4136c566a962/scans";

        filter.doFilter(request(path), new MockHttpServletResponse(), chain);
        var blockedResponse = new MockHttpServletResponse();
        filter.doFilter(request(path), blockedResponse, chain);

        assertThat(passed).hasValue(1);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isNotBlank();
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
