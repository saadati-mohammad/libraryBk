package ir.iau.library.repository;

import ir.iau.library.entity.MemberAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface MemberAccountRepository extends JpaRepository<MemberAccount, Long> {
    @EntityGraph(attributePaths = "person")
    Optional<MemberAccount> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    java.util.Optional<MemberAccount> findByPersonId(Long personId);
}
