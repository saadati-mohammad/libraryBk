package ir.iau.library.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process abuse throttle for public credential endpoints. Never trusts client-supplied IP headers. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int resetLimit;
    private final Duration resetWindow;
    private final int deviceLimit;
    private final Duration deviceWindow;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(ObjectMapper mapper,
            @Value("${app.security.rate-limit.login.limit:20}") int loginLimit,
            @Value("${app.security.rate-limit.login.window:PT15M}") Duration loginWindow,
            @Value("${app.security.rate-limit.reset.limit:5}") int resetLimit,
            @Value("${app.security.rate-limit.reset.window:PT1H}") Duration resetWindow,
            @Value("${app.security.rate-limit.device.limit:60}") int deviceLimit,
            @Value("${app.security.rate-limit.device.window:PT1M}") Duration deviceWindow) {
        this.mapper = mapper;
        this.loginLimit = Math.max(1, loginLimit);
        this.loginWindow = loginWindow.isNegative() || loginWindow.isZero() ? Duration.ofMinutes(15) : loginWindow;
        this.resetLimit = Math.max(1, resetLimit);
        this.resetWindow = resetWindow.isNegative() || resetWindow.isZero() ? Duration.ofHours(1) : resetWindow;
        this.deviceLimit = Math.max(1, deviceLimit);
        this.deviceWindow = deviceWindow.isNegative() || deviceWindow.isZero() ? Duration.ofMinutes(1) : deviceWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !"/api/auth/login".equals(path) && !"/api/auth/forgot-password".equals(path)
                && !path.matches("/api/device-inputs/\\d+/heartbeat");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean login = "/api/auth/login".equals(path);
        boolean device = path.startsWith("/api/device-inputs/");
        Duration window = login ? loginWindow : device ? deviceWindow : resetWindow;
        int limit = login ? loginLimit : device ? deviceLimit : resetLimit;
        String key = path + "|" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Window bucket = windows.computeIfAbsent(key, ignored -> new Window());
        long retryMillis;
        synchronized (bucket) {
            long cutoff = now - window.toMillis();
            while (!bucket.events.isEmpty() && bucket.events.peekFirst() <= cutoff) bucket.events.removeFirst();
            if (bucket.events.size() >= limit) {
                retryMillis = Math.max(1, window.toMillis() - (now - bucket.events.peekFirst()));
            } else {
                bucket.events.addLast(now);
                retryMillis = 0;
            }
        }
        if (retryMillis > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(Math.max(1, (retryMillis + 999) / 1000)));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), ApiResponse.error("درخواست‌های زیادی ثبت شده است؛ کمی بعد دوباره تلاش کنید."));
            return;
        }
        if (windows.size() > 10_000) prune(now);
        chain.doFilter(request, response);
    }

    private void prune(long now) {
        windows.forEach((key, bucket) -> {
            synchronized (bucket) {
                Duration longestWindow = loginWindow.compareTo(resetWindow) >= 0 ? loginWindow : resetWindow;
                if (deviceWindow.compareTo(longestWindow) > 0) longestWindow = deviceWindow;
                if (bucket.events.isEmpty() || now - bucket.events.peekLast() > longestWindow.toMillis()) {
                    windows.remove(key, bucket);
                }
            }
        });
    }

    private static final class Window {
        private final ArrayDeque<Long> events = new ArrayDeque<>();
    }
}
