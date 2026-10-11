package ir.iau.library.dto;

import java.time.Instant;

/** One-time capability used only to connect a companion phone scanner. */
public record ScannerSessionDto(String sessionId, String pairingToken, Instant expiresAt) { }
