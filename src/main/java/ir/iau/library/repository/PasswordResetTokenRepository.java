package ir.iau.library.repository;

import ir.iau.library.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    java.util.List<PasswordResetToken> findAllByUsernameIgnoreCase(String username);
    Optional<PasswordResetToken> findTopByUsernameIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(String username, Instant after);
    void deleteByUsernameIgnoreCaseAndUsedAtIsNull(String username);
    void deleteByUsernameIgnoreCaseAndUsedAtIsNullAndIdNot(String username, Long id);
    long deleteByExpiresAtBefore(java.time.Instant instant);
}
