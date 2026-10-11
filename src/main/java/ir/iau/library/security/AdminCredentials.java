package ir.iau.library.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ir.iau.library.entity.MemberAccount;
import ir.iau.library.repository.MemberAccountRepository;

/**
 * Resolves database-backed member accounts and the configured administrative principal.
 *
 * <p>Members authenticate with their database-backed email and BCrypt password hash. The
 * administrative account remains configured by deployment ({@code APP_ADMIN_USERNAME} /
 * {@code APP_ADMIN_PASSWORD}); the clear-text value is encoded at startup and never persisted.
 *
 * <p>Roles are assigned by this service, never by a public registration request.
 */
@Service
public class AdminCredentials implements UserDetailsService {

    private final String username;
    private final String encodedPassword;
    private final MemberAccountRepository memberAccounts;

    public AdminCredentials(
            @Value("${app.security.admin.username:admin}") String username,
            @Value("${app.security.admin.password:}") String rawPassword,
            PasswordEncoder passwordEncoder,
            MemberAccountRepository memberAccounts) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalStateException(
                    "app.security.admin.password (env APP_ADMIN_PASSWORD) must be set; refusing to start without a login credential.");
        }
        this.username = username;
        this.encodedPassword = passwordEncoder.encode(rawPassword);
        this.memberAccounts = memberAccounts;
    }

    @Override
    public UserDetails loadUserByUsername(String candidate) throws UsernameNotFoundException {
        MemberAccount member = memberAccounts.findByUsernameIgnoreCase(candidate).orElse(null);
        if (member != null) {
            return User.withUsername(member.getUsername())
                    .password(member.getPasswordHash())
                    .roles("MEMBER")
                    .disabled(!Boolean.TRUE.equals(member.getPerson().getActive()))
                    .build();
        }
        if (!username.equals(candidate)) throw new UsernameNotFoundException("Unknown user");
        return User.withUsername(username)
                .password(encodedPassword)
                .roles("ADMIN")
                .build();
    }
}
