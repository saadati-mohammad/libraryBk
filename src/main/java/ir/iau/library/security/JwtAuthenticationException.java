package ir.iau.library.security;

import org.springframework.messaging.MessagingException;

/**
 * Thrown when a STOMP frame cannot be authenticated or authorized. Failing the interceptor
 * aborts the offending frame (or the whole connection, for CONNECT), so the client cannot
 * proceed with an unauthenticated or spoofed identity.
 */
public class JwtAuthenticationException extends MessagingException {

    public JwtAuthenticationException(String message) {
        super(message);
    }
}
