package ir.iau.library.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;

/**
 * Reads a {@code Authorization: Bearer <jwt>} header, verifies the token, and populates the
 * {@link SecurityContextHolder} for the request.
 *
 * <p>An absent or invalid token is not an error here: the request simply proceeds
 * unauthenticated and the {@link SecurityConfig} authorization rules decide the outcome.
 * That keeps this filter single-purpose and lets Spring Security produce the 401/403.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthVersionService authVersionService;
    private final ObjectProvider<UserDetailsService> userDetailsServices;

    public JwtAuthenticationFilter(JwtService jwtService, AuthVersionService authVersionService,
                                   ObjectProvider<UserDetailsService> userDetailsServices) {
        this.jwtService = jwtService;
        this.authVersionService = authVersionService;
        this.userDetailsServices = userDetailsServices;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parse(token);
                String username = claims.getSubject();
                if (username != null) {
                    Object versionClaim = claims.get("ver");
                    long tokenVersion = versionClaim instanceof Number number ? number.longValue() : 0L;
                    if (tokenVersion != authVersionService.currentVersion(username)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // Re-read the account on every request so deactivation and role changes
                    // take effect immediately instead of waiting for an old JWT to expire.
                    UserDetails account = userDetailsServices.getObject().loadUserByUsername(username);
                    if (!account.isEnabled()) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(
                            account.getUsername(), null, account.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Invalid/expired signature: leave the context unauthenticated. Do not log the
                // token itself (it is a credential).
                log.debug("Rejected bearer token: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
