package ir.iau.library.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ir.iau.library.entity.MemberAccount;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.StaffAccountRepository;

/**
 * Resolves database-backed staff and member identities and their current roles.
 *
 * <p>Staff and members authenticate against database-backed BCrypt password hashes.
 * Deployment credentials are consumed once by {@link StaffAccountBootstrap} to seed the
 * first administrator; subsequent staff-account changes are database-backed.
 */
@Service
public class AdminCredentials implements UserDetailsService {

    private final MemberAccountRepository memberAccounts;
    private final StaffAccountRepository staffAccounts;
    private final String bootstrapUsername;

    public AdminCredentials(
            @Value("${app.security.admin.username:admin}") String bootstrapUsername,
            MemberAccountRepository memberAccounts,
            StaffAccountRepository staffAccounts) {
        this.bootstrapUsername = bootstrapUsername;
        this.memberAccounts = memberAccounts;
        this.staffAccounts = staffAccounts;
    }

    public boolean isBootstrapUsername(String candidate) {
        return candidate != null && bootstrapUsername.equalsIgnoreCase(candidate.strip());
    }

    @Override
    public UserDetails loadUserByUsername(String candidate) throws UsernameNotFoundException {
        var staff = staffAccounts.findByUsernameIgnoreCase(candidate).orElse(null);
        if (staff != null) {
            return User.withUsername(staff.getUsername())
                    .password(staff.getPasswordHash())
                    .roles(staff.getRole().name())
                    .disabled(!staff.isActive())
                    .build();
        }
        MemberAccount member = memberAccounts.findByUsernameIgnoreCase(candidate).orElse(null);
        if (member != null) {
            return User.withUsername(member.getUsername())
                    .password(member.getPasswordHash())
                    .roles("MEMBER")
                    .disabled(!Boolean.TRUE.equals(member.getPerson().getActive()))
                    .build();
        }
        throw new UsernameNotFoundException("Unknown user");
    }
}
