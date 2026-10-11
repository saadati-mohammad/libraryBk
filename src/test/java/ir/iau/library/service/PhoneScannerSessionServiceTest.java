package ir.iau.library.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneScannerSessionServiceTest {
    @Test
    void relaysValidIsbnOnlyToThePairedDesktopAndSuppressesCameraFrameDuplicates() {
        PhoneScannerSessionService service = new PhoneScannerSessionService();
        var session = service.create();

        assertThatThrownBy(() -> service.submit(session.sessionId(), "wrong-token", "9780306406157"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        var accepted = service.submit(session.sessionId(), session.pairingToken(), "978-0-306-40615-7");
        var duplicate = service.submit(session.sessionId(), session.pairingToken(), "9780306406157");

        assertThat(accepted.sequence()).isEqualTo(1);
        assertThat(duplicate.sequence()).isEqualTo(accepted.sequence());
        assertThat(service.poll(session.sessionId(), 0)).containsExactly(accepted);
        assertThat(service.poll(session.sessionId(), accepted.sequence())).isEmpty();
    }

    @Test
    void rejectsInvalidIsbnAndClosedPairingSessions() {
        PhoneScannerSessionService service = new PhoneScannerSessionService();
        var session = service.create();
        assertThatThrownBy(() -> service.submit(session.sessionId(), session.pairingToken(), "not-an-isbn"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        service.close(session.sessionId());
        assertThatThrownBy(() -> service.poll(session.sessionId(), 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("410 GONE");
    }

    @Test
    void bindsRegisteredPhoneSessionToFirstAuthenticatedDevice() {
        PhoneScannerSessionService service = new PhoneScannerSessionService();
        var session = service.create();

        service.submit(session.sessionId(), session.pairingToken(), "9780306406157", 42L);
        assertThatThrownBy(() -> service.submit(session.sessionId(), session.pairingToken(), "9780306406157", 43L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> service.submit(session.sessionId(), session.pairingToken(), "9780306406157", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void boundsActiveSessionsPerStaffAccount() {
        PhoneScannerSessionService service = new PhoneScannerSessionService();
        for (int i = 0; i < 10; i++) service.create("busy-librarian");

        assertThatThrownBy(() -> service.create("busy-librarian"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
        assertThat(service.create("another-librarian")).isNotNull();
    }
}
