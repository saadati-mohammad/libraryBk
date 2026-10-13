package ir.iau.library.repository;

import ir.iau.library.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    java.util.List<PasswordResetToken> findAllByUsernameIgnoreCase(String username);
    Optional<PasswordResetToken> findTopByUsernameIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(String username, Instant after);
    void deleteByUsernameIgnoreCaseAndUsedAtIsNull(String username);
    void deleteByUsernameIgnoreCaseAndUsedAtIsNullAndIdNot(String username, Long id);
    long deleteByExpiresAtBefore(java.time.Instant instant);
}
