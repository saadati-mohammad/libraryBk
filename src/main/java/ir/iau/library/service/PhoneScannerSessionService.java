package ir.iau.library.service;

import ir.iau.library.dto.ScannedBarcodeDto;
import ir.iau.library.dto.ScannerSessionDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, in-memory barcode relay; it stores no account credentials or book data. */
@Service
public class PhoneScannerSessionService {
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_PENDING_SCANS = 50;
    private static final long DUPLICATE_WINDOW_MILLIS = 1_200;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public ScannerSessionDto create() {
        purgeExpired();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String id = UUID.randomUUID().toString();
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
        sessions.put(id, new Session(token, expiresAt));
        return new ScannerSessionDto(id, token, expiresAt);
    }

    public ScannedBarcodeDto submit(String id, String token, String rawBarcode) {
        Session session = requireSession(id);
        if (!constantTimeEquals(session.token, token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid scanner pairing token");
        }
        String barcode = Isbn.normalize(rawBarcode);
        if (!Isbn.isValid(barcode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only valid ISBN-10 or ISBN-13 barcodes are accepted");
        }
        synchronized (session) {
            long now = System.currentTimeMillis();
            if (barcode.equals(session.lastBarcode) && now - session.lastScanMillis < DUPLICATE_WINDOW_MILLIS) {
                return new ScannedBarcodeDto(session.sequence, barcode, Instant.ofEpochMilli(session.lastScanMillis));
            }
            if (session.pending.size() >= MAX_PENDING_SCANS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Desktop scanner queue is full");
            }
            session.sequence++;
            session.lastBarcode = barcode;
            session.lastScanMillis = now;
            ScannedBarcodeDto scan = new ScannedBarcodeDto(session.sequence, barcode, Instant.ofEpochMilli(now));
            session.pending.addLast(scan);
            return scan;
        }
    }

    public List<ScannedBarcodeDto> poll(String id, long afterSequence) {
        Session session = requireSession(id);
        synchronized (session) {
            List<ScannedBarcodeDto> scans = new ArrayList<>();
            while (!session.pending.isEmpty()) {
                ScannedBarcodeDto next = session.pending.peekFirst();
                if (next.sequence() > afterSequence) break;
                session.pending.removeFirst();
            }
            for (ScannedBarcodeDto scan : session.pending) {
                if (scan.sequence() > afterSequence) scans.add(scan);
            }
            return List.copyOf(scans);
        }
    }

    public void close(String id) {
        sessions.remove(id);
    }

    private Session requireSession(String id) {
        Session session = sessions.get(id);
        if (session == null || !session.expiresAt.isAfter(Instant.now())) {
            sessions.remove(id);
            throw new ResponseStatusException(HttpStatus.GONE, "Scanner pairing has expired");
        }
        return session;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
    }

    private static boolean constantTimeEquals(String expected, String candidate) {
        if (candidate == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Session {
        private final String token;
        private final Instant expiresAt;
        private final ArrayDeque<ScannedBarcodeDto> pending = new ArrayDeque<>();
        private long sequence;
        private String lastBarcode;
        private long lastScanMillis;

        private Session(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
