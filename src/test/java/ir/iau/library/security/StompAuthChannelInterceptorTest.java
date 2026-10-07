package ir.iau.library.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ir.iau.library.dto.WebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the STOMP authentication boundary. These exercise the exact interceptor the
 * broker uses on its inbound channel, with real JWTs signed by a real {@link JwtService}, so
 * they prove: unauthenticated/invalid/expired CONNECT is rejected, a valid CONNECT establishes
 * the principal, and SEND/SUBSCRIBE cannot spoof or overreach that principal.
 */
class StompAuthChannelInterceptorTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";
    private static final String OTHER_SECRET = "another-secret-another-secret-1234567890123";

    private JwtService jwtService;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600);
        interceptor = new StompAuthChannelInterceptor(jwtService);
        channel = new MessageChannel() {
            @Override public boolean send(Message<?> message) { return true; }
            @Override public boolean send(Message<?> message, long timeout) { return true; }
        };
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<WebSocketMessage> sendMessage(StompHeaderAccessor accessor, WebSocketMessage payload) {
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    @Test
    void validTokenEstablishesPrincipalOnConnect() {
        String token = jwtService.generateToken("admin", "ADMIN");
        Message<?> msg = connectMessage("Bearer " + token);

        Message<?> result = interceptor.preSend(msg, channel);

        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        assertThat(out.getUser()).isNotNull();
        assertThat(out.getUser().getName()).isEqualTo("admin");
    }

    @Test
    void missingTokenIsRejected() {
        Message<?> msg = connectMessage(null);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void invalidTokenIsRejected() {
        Message<?> msg = connectMessage("Bearer not.a.jwt");
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void tokenSignedWithWrongKeyIsRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();
        Message<?> msg = connectMessage("Bearer " + forged);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -60); // already expired on issue
        String expired = shortLived.generateToken("admin", "ADMIN");
        Message<?> msg = connectMessage("Bearer " + expired);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void sendWithMatchingSenderIsAccepted() {
        StompHeaderAccessor accessor = authenticatedAccessor("admin", StompCommand.SEND);
        WebSocketMessage payload = WebSocketMessage.builder().sender("admin").recipient("bob").build();

        Message<?> result = interceptor.preSend(sendMessage(accessor, payload), channel);
        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        assertThat(out.getUser().getName()).isEqualTo("admin");
    }

    @Test
    void sendImpersonatingAnotherUserIsRejected() {
        StompHeaderAccessor accessor = authenticatedAccessor("admin", StompCommand.SEND);
        WebSocketMessage payload = WebSocketMessage.builder().sender("victim").recipient("bob").build();

        assertThatThrownBy(() -> interceptor.preSend(sendMessage(accessor, payload), channel))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("does not match authenticated principal");
    }

    @Test
    void subscribeToOwnQueueIsAllowed() {
        StompHeaderAccessor accessor = authenticatedAccessor("admin", StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/user/admin");

        interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
        // no exception == allowed
    }

    @Test
    void subscribeToAnotherUsersQueueIsRejected() {
        StompHeaderAccessor accessor = authenticatedAccessor("admin", StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/user/victim");

        assertThatThrownBy(() ->
                interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void subscribeToSharedTopicIsRejected() {
        StompHeaderAccessor accessor = authenticatedAccessor("admin", StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/broadcast");

        assertThatThrownBy(() ->
                interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void unauthenticatedSendIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        WebSocketMessage payload = WebSocketMessage.builder().sender("admin").build();

        assertThatThrownBy(() -> interceptor.preSend(sendMessage(accessor, payload), channel))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    private StompHeaderAccessor authenticatedAccessor(String username, StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                username, null, java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        accessor.setLeaveMutable(true);
        return accessor;
    }
}
