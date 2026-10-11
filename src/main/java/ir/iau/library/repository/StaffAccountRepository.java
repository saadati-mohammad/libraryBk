package ir.iau.library.repository;

import ir.iau.library.entity.StaffAccount;
import ir.iau.library.entity.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {
    Optional<StaffAccount> findByUsernameIgnoreCase(String username);
    Optional<StaffAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    List<StaffAccount> findAllByOrderByCreatedAtDesc();
    long countByRoleAndActiveTrue(StaffRole role);
}
