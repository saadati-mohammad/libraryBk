package ir.iau.library.service;

import ir.iau.library.dto.MessageDto;
import ir.iau.library.dto.WebSocketMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the WebSocket timestamp (defect 6).
 *
 * <p>The mapper used to send the zone <em>offset</em> in millis
 * ({@code getOffset(...).getTotalSeconds() * 1000L} — 3.5 hours in Tehran) as the message
 * timestamp, so every client rendered the message as 1970. It must be epoch millis of
 * {@code createDate}, which is exactly what {@code fromWebSocketMessage} parses back.
 */
class MessageMapperTest {

    private final MessageMapper mapper = new MessageMapper();

    @Test
    void webSocketTimestampIsEpochMillisOfCreateDate() {
        LocalDateTime createDate = LocalDateTime.of(2024, 1, 15, 10, 30);
        MessageDto dto = MessageDto.builder()
                .id(1L)
                .sender("alice")
                .recipient("bob")
                .message("hello")
                .createDate(createDate)
                .build();

        WebSocketMessage ws = mapper.toWebSocketMessage(dto);

        long expected = createDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertThat(ws.getTimestamp()).isEqualTo(expected);
        // The old bug produced the zone offset (hours, not decades).
        assertThat(ws.getTimestamp()).isGreaterThan(1_000_000_000_000L);
    }

    @Test
    void timestampRoundTripsBackToTheOriginalLocalDateTime() {
        LocalDateTime createDate = LocalDateTime.of(2024, 6, 1, 23, 45, 30);
        MessageDto dto = MessageDto.builder()
                .id(2L)
                .sender("alice")
                .recipient("bob")
                .createDate(createDate)
                .build();

        MessageDto roundTripped = mapper.fromWebSocketMessage(mapper.toWebSocketMessage(dto));

        assertThat(roundTripped.getCreateDate()).isEqualTo(createDate);
    }

    @Test
    void missingCreateDateFallsBackToNow() {
        long before = System.currentTimeMillis();
        MessageDto dto = MessageDto.builder().id(3L).sender("alice").recipient("bob").build();

        WebSocketMessage ws = mapper.toWebSocketMessage(dto);

        assertThat(ws.getTimestamp()).isBetween(before, System.currentTimeMillis());
    }
}
