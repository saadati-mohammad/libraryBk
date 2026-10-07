package ir.iau.library.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Supplies the single administrative principal used to protect the back-office API.
 *
 * <p>The existing domain has no credential store: {@code User} holds chat/profile data and
 * no password column, and no login UI ever existed. Rather than invent a multi-user product
 * model, this service authenticates one operator whose username/password come from
 * configuration ({@code APP_ADMIN_USERNAME} / {@code APP_ADMIN_PASSWORD}). The password is
 * BCrypt-hashed at construction and never stored or logged in plaintext.
 *
 * <p>This is a deliberate, minimal, replaceable boundary: when a real identity store is
 * introduced, swap this {@link UserDetailsService} for a JPA-backed one without touching the
 * filter chain or the API contract.
 */
@Service
public class AdminCredentials implements UserDetailsService {

    private final String username;
    private final String encodedPassword;

    public AdminCredentials(
            @Value("${app.security.admin.username:admin}") String username,
            @Value("${app.security.admin.password:}") String rawPassword,
            PasswordEncoder passwordEncoder) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalStateException(
                    "app.security.admin.password (env APP_ADMIN_PASSWORD) must be set; refusing to start without a login credential.");
        }
        this.username = username;
        this.encodedPassword = passwordEncoder.encode(rawPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String candidate) throws UsernameNotFoundException {
        if (!username.equals(candidate)) {
            throw new UsernameNotFoundException("Unknown user: " + candidate);
        }
        return User.withUsername(username)
                .password(encodedPassword)
                .roles("ADMIN")
                .build();
    }
}
