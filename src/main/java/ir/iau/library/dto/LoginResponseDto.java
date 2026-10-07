package ir.iau.library.dto;

/**
 * Successful login payload. {@code expiresInSeconds} lets the client schedule a proactive
 * logout instead of waiting for the first 401 after expiry.
 */
public record LoginResponseDto(String token, String username, String role, long expiresInSeconds) {
}
