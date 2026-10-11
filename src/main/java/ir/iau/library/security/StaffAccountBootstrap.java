package ir.iau.library.security;

import ir.iau.library.entity.StaffAccount;
import ir.iau.library.entity.StaffRole;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.StaffAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the first administrator from deployment secrets once; afterwards it is an ordinary managed account. */
@Component
public class StaffAccountBootstrap implements ApplicationRunner {
    private final StaffAccountRepository staffAccounts;
    private final MemberAccountRepository memberAccounts;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public StaffAccountBootstrap(StaffAccountRepository staffAccounts, MemberAccountRepository memberAccounts,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.admin.username:admin}") String username,
            @Value("${app.security.admin.password:}") String password,
            @Value("${app.security.admin.email:}") String email) {
        this.staffAccounts = staffAccounts;
        this.memberAccounts = memberAccounts;
        this.passwordEncoder = passwordEncoder;
        this.username = username.strip().toLowerCase(java.util.Locale.ROOT);
        this.password = password;
        this.email = email == null || email.isBlank() ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (staffAccounts.count() > 0) return;
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD is required to create the first administrator account.");
        }
        if (memberAccounts.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("The configured initial admin username is already used by a member account.");
        }
        if (email != null && memberAccounts.findByPersonEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("The configured initial admin email is already used by a member account.");
        }
        StaffAccount admin = new StaffAccount();
        admin.setUsername(username);
        admin.setDisplayName(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(StaffRole.ADMIN);
        admin.setActive(true);
        staffAccounts.save(admin);
    }
}
