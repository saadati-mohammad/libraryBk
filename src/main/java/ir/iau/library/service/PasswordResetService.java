package ir.iau.library.service;

import ir.iau.library.entity.PasswordResetToken;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.PasswordResetTokenRepository;
import ir.iau.library.repository.StaffAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_RESPONSE = "اگر حسابی با این ایمیل وجود داشته باشد، لینک بازیابی برای آن ارسال می‌شود.";

    private final PasswordResetTokenRepository tokens;
    private final StaffAccountRepository staffAccounts;
    private final MemberAccountRepository memberAccounts;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.password-reset.enabled:true}") private boolean enabled;
    @Value("${app.password-reset.public-url:}") private String publicUrl;
    @Value("${spring.mail.from:}") private String fromAddress;

    @Transactional
    public String request(String requestedEmail) {
        if (!enabled) throw unavailable("Password recovery is currently unavailable.");
        if (requestedEmail == null) return GENERIC_RESPONSE;
        String email = requestedEmail.strip().toLowerCase(Locale.ROOT);
        var staff = staffAccounts.findByEmailIgnoreCase(email).orElse(null);
        var member = memberAccounts.findByPersonEmailIgnoreCase(email).orElse(null);
        // Ambiguous addresses are never used to reset an account.
        if ((staff == null) == (member == null)) return GENERIC_RESPONSE;
        String username = staff != null ? staff.getUsername() : member.getUsername();
        Instant now = Instant.now();
        if (tokens.findTopByUsernameIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
                username, now.minus(60, ChronoUnit.SECONDS)).isPresent()) return GENERIC_RESPONSE;

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        PasswordResetToken reset = new PasswordResetToken();
        reset.setTokenHash(hash(rawToken));
        reset.setUsername(username);
        reset.setCreatedAt(now);
        reset.setExpiresAt(now.plus(20, ChronoUnit.MINUTES));
        tokens.deleteByUsernameIgnoreCaseAndUsedAtIsNull(username);
        tokens.save(reset);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || publicUrl == null || publicUrl.isBlank() || fromAddress == null || fromAddress.isBlank()) {
            tokens.delete(reset);
            log.warn("Password reset email is not configured; no reset link was issued.");
            throw unavailable("Password recovery email delivery is not configured.");
        }
        String link = publicUrl.replaceAll("/+$", "") + "/reset-password?token="
                + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("بازیابی رمز عبور سامانه کتابخانه");
        message.setText("برای تعیین رمز عبور جدید، حداکثر تا ۲۰ دقیقه آینده این پیوند را باز کنید:\n\n"
                + link + "\n\nاگر این درخواست از طرف شما نبوده، این پیام را نادیده بگیرید.");
        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            tokens.delete(reset);
            log.warn("Password reset email delivery failed.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password recovery email delivery is temporarily unavailable.", exception);
        }
        return GENERIC_RESPONSE;
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.length() < 32 || rawToken.length() > 200) {
            throw new EntityNotFoundException("لینک بازیابی نامعتبر یا منقضی شده است");
        }
        PasswordResetToken reset = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new EntityNotFoundException("لینک بازیابی نامعتبر یا منقضی شده است"));
        Instant now = Instant.now();
        if (reset.getUsedAt() != null || !reset.getExpiresAt().isAfter(now)) {
            throw new EntityNotFoundException("لینک بازیابی نامعتبر یا منقضی شده است");
        }
        var staff = staffAccounts.findByUsernameIgnoreCase(reset.getUsername()).orElse(null);
        if (staff != null) {
            staff.setPasswordHash(passwordEncoder.encode(newPassword));
            staff.setAuthVersion(staff.getAuthVersion() + 1);
        } else {
            var member = memberAccounts.findByUsernameIgnoreCase(reset.getUsername())
                    .orElseThrow(() -> new EntityNotFoundException("لینک بازیابی نامعتبر یا منقضی شده است"));
            member.setPasswordHash(passwordEncoder.encode(newPassword));
            member.setAuthVersion(member.getAuthVersion() + 1);
        }
        reset.setUsedAt(now);
        tokens.deleteByUsernameIgnoreCaseAndUsedAtIsNullAndIdNot(reset.getUsername(), reset.getId());
        tokens.save(reset);
    }

    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        tokens.deleteByExpiresAtBefore(Instant.now());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
