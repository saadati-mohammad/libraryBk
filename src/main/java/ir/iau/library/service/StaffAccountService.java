package ir.iau.library.service;

import ir.iau.library.dto.CreateStaffAccountRequest;
import ir.iau.library.dto.StaffAccountDto;
import ir.iau.library.dto.UpdateStaffAccountRequest;
import ir.iau.library.entity.StaffAccount;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.StaffAccountRepository;
import ir.iau.library.security.AdminCredentials;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;


@Service
@Transactional
@RequiredArgsConstructor
public class StaffAccountService {
    private final StaffAccountRepository staffAccounts;
    private final MemberAccountRepository memberAccounts;
    private final PasswordEncoder passwordEncoder;
    private final AdminCredentials adminCredentials;

    public List<StaffAccountDto> list() {
        return staffAccounts.findAllByOrderByCreatedAtDesc().stream().map(StaffAccountDto::from).toList();
    }

    public StaffAccountDto create(CreateStaffAccountRequest request) {
        String username = normalize(request.username());
        ensureUsernameAvailable(username);
        StaffAccount account = new StaffAccount();
        account.setUsername(username);
        account.setDisplayName(request.displayName().strip());
        account.setEmail(request.email().strip().toLowerCase(Locale.ROOT));
        ensureEmailAvailable(account.getEmail(), null);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(request.role());
        account.setActive(true);
        return StaffAccountDto.from(staffAccounts.save(account));
    }

    public StaffAccountDto update(long id, UpdateStaffAccountRequest request, String actor) {
        StaffAccount account = find(id);
        if (account.getUsername().equalsIgnoreCase(actor)
                && (!account.isActive() || account.getRole() != request.role())) {
            throw new AccessDeniedException("You cannot disable or change your own role");
        }
        if (account.isActive() && account.getRole() == ir.iau.library.entity.StaffRole.ADMIN
                && request.role() != ir.iau.library.entity.StaffRole.ADMIN
                && staffAccounts.countByRoleAndActiveTrue(ir.iau.library.entity.StaffRole.ADMIN) <= 1) {
            throw new IllegalStateException("At least one active administrator must remain");
        }
        account.setDisplayName(request.displayName().strip());
        account.setEmail(request.email().strip().toLowerCase(Locale.ROOT));
        ensureEmailAvailable(account.getEmail(), account.getId());
        account.setRole(request.role());
        if (request.password() != null && !request.password().isBlank()) {
            account.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return StaffAccountDto.from(account);
    }

    /** Deactivation preserves the staff record and immediately invalidates its existing JWTs. */
    public StaffAccountDto deactivate(long id, String actor) {
        StaffAccount account = find(id);
        if (account.getUsername().equalsIgnoreCase(actor)) {
            throw new AccessDeniedException("You cannot deactivate your own account");
        }
        if (account.isActive() && account.getRole() == ir.iau.library.entity.StaffRole.ADMIN
                && staffAccounts.countByRoleAndActiveTrue(ir.iau.library.entity.StaffRole.ADMIN) <= 1) {
            throw new IllegalStateException("At least one active administrator must remain");
        }
        account.setActive(false);
        return StaffAccountDto.from(account);
    }

    public StaffAccountDto reactivate(long id) {
        StaffAccount account = find(id);
        account.setActive(true);
        return StaffAccountDto.from(account);
    }

    private StaffAccount find(long id) {
        return staffAccounts.findById(id).orElseThrow(() -> new EntityNotFoundException("Staff account not found"));
    }

    private void ensureUsernameAvailable(String username) {
        if (adminCredentials.isBootstrapUsername(username)
                || staffAccounts.existsByUsernameIgnoreCase(username)
                || memberAccounts.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Username is already in use");
        }
    }

    private void ensureEmailAvailable(String email, Long excludeId) {
        var existingStaff = staffAccounts.findByEmailIgnoreCase(email);
        if ((existingStaff.isPresent() && !existingStaff.get().getId().equals(excludeId))
                || memberAccounts.findByPersonEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("Email is already in use");
        }
    }

    private static String normalize(String username) { return username.strip().toLowerCase(Locale.ROOT); }
}
