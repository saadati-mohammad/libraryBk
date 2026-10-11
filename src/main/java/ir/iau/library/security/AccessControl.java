package ir.iau.library.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

/**
 * Principal-based authorization helpers for the REST surface.
 *
 * <p>Previously no controller ever looked at the authenticated principal, so any holder of
 * a valid token could read, rewrite or delete any other user's messages and attachments
 * (a classic IDOR). These helpers make the authenticated identity the authority for every
 * chat/message operation.
 *
 * <p>Every denial throws {@link AccessDeniedException}, which
 * {@link ir.iau.library.exception.GlobalExceptionHandler} maps to HTTP 403 with the regular
 * {@code ApiResponse} envelope.
 */
public final class AccessControl {

    private AccessControl() {
    }

    /**
     * Resolves the authenticated username for the current request.
     *
     * <p>The controller parameter is used when present; otherwise the security context is
     * consulted, so a controller cannot accidentally run unauthenticated just because the
     * servlet request wrapper did not expose the principal.
     */
    public static String requireUsername(Principal principal) {
        String username = principal != null ? principal.getName() : null;
        if (username == null || username.isBlank()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                username = authentication.getName();
            }
        }
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authentication required");
        }
        return username;
    }

    /** True when {@code username} is the message's sender or its recipient. */
    public static boolean isParticipant(String username, String sender, String recipient) {
        return username != null
                && (username.equals(sender) || username.equals(recipient));
    }

    /**
     * Fails closed unless the caller is the sender or the recipient of the message.
     *
     * @param resource short description of what is being accessed, used for the log/error
     *                 message (e.g. {@code "message 42"} or {@code "file 7"})
     */
    public static void requireParticipant(String username, String sender, String recipient, String resource) {
        if (!isParticipant(username, sender, recipient)) {
            throw new AccessDeniedException(
                    "User '" + username + "' does not participate in " + resource);
        }
    }

    /** Fails closed unless the caller is exactly the given user. */
    public static void requireSelf(String username, String owner, String what) {
        if (owner == null || !owner.equals(username)) {
            throw new AccessDeniedException("User '" + username + "' is not allowed to access " + what);
        }
    }
}
