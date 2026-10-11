package ir.iau.library.security;

import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.StaffAccountRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Reads the account's current credential generation for access-token issuance and validation. */
@Service
public class AuthVersionService {
    private final StaffAccountRepository staffAccounts;
    private final MemberAccountRepository memberAccounts;

    public AuthVersionService(StaffAccountRepository staffAccounts, MemberAccountRepository memberAccounts) {
        this.staffAccounts = staffAccounts;
        this.memberAccounts = memberAccounts;
    }

    public long currentVersion(String username) {
        var staff = staffAccounts.findByUsernameIgnoreCase(username).orElse(null);
        if (staff != null) return staff.getAuthVersion();
        return memberAccounts.findByUsernameIgnoreCase(username)
                .map(account -> account.getAuthVersion())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
