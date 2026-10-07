package ir.iau.library.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import ir.iau.library.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP frames so the WebSocket layer never trusts a client-supplied username.
 *
 * <p>On {@code CONNECT} the bearer token is read from the STOMP native {@code Authorization}
 * header and verified with the same {@link JwtService} that secures the REST API. The verified
 * subject becomes the STOMP session principal. Missing, invalid, or expired tokens are
 * rejected by throwing, which aborts the connection.
 *
 * <p>On {@code SEND} the identity field in the payload ({@code sender}) must match the
 * authenticated principal; a mismatch is rejected, so a client cannot impersonate another user.
 *
 * <p>On {@code SUBSCRIBE} a client may only subscribe to its own private queue
 * ({@code /topic/user/{principal}}); subscribing to another user's queue or to a shared
 * server topic is rejected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern USER_DESTINATION = Pattern.compile("^/topic/user/([^/]+)$");

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticateConnect(accessor);
            case SEND -> enforceSenderIdentity(accessor, message);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            default -> { /* nothing to enforce for other frames */ }
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            throw new JwtAuthenticationException("Missing bearer token on STOMP CONNECT");
        }
        try {
            Claims claims = jwtService.parse(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            if (username == null || username.isBlank()) {
                throw new JwtAuthenticationException("Token has no subject");
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "ADMIN" : role))));
            accessor.setUser(authentication);
            log.debug("Authenticated STOMP CONNECT for principal '{}'", username);
        } catch (JwtException | IllegalArgumentException ex) {
            // Do not log the token itself; it is a credential.
            throw new JwtAuthenticationException("Invalid or expired token on STOMP CONNECT");
        }
    }

    /**
     * Force {@code sender} to be the authenticated principal. A payload claiming a different
     * sender is rejected outright rather than silently rewritten, so impersonation attempts
     * are visible and cannot succeed.
     */
    private void enforceSenderIdentity(StompHeaderAccessor accessor, Message<?> message) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new JwtAuthenticationException("Unauthenticated SEND is not allowed");
        }
        Object payload = message.getPayload();
        if (payload instanceof WebSocketMessage wsMessage) {
            String claimedSender = wsMessage.getSender();
            if (claimedSender != null && !claimedSender.equals(principal.getName())) {
                throw new JwtAuthenticationException(
                        "Sender '" + claimedSender + "' does not match authenticated principal '"
                                + principal.getName() + "'");
            }
            // Ensure downstream code always sees the authenticated identity.
            wsMessage.setSender(principal.getName());
        }
    }

    /**
     * A client may only subscribe to its own private user queue. Shared/internal topics
     * (broadcast, rooms) are server-published and are not client-subscribable.
     */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new JwtAuthenticationException("Unauthenticated SUBSCRIBE is not allowed");
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new JwtAuthenticationException("SUBSCRIBE without destination");
        }
        var matcher = USER_DESTINATION.matcher(destination);
        if (matcher.matches()) {
            String targetUser = matcher.group(1);
            if (!Objects.equals(targetUser, principal.getName())) {
                throw new JwtAuthenticationException(
                        "Cannot subscribe to another user's queue (" + destination + ")");
            }
            return;
        }
        // Any other destination is not client-subscribable under the current model.
        throw new JwtAuthenticationException("Subscription to '" + destination + "' is not permitted");
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}
