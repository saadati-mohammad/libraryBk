package ir.iau.library.service;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.MessageStats;
import ir.iau.library.entity.Message;
import ir.iau.library.repository.MessageRepository;
import ir.iau.library.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link MessageService#getMessageStats(String)}.
 *
 * <p>The aggregate query returns an {@code Object[]} whose entries may be null (a user with
 * no messages) or a driver-specific numeric type. Previously the code cast directly to
 * {@code Long} and would NPE / ClassCastException; these tests pin the defensive behaviour.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceStatsTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private MessageService service;

    @Test
    void getMessageStats_handlesNullAggregateRow() {
        when(messageRepository.getMessageStats(eq("ghost")))
                .thenReturn(null);
        when(messageRepository.findMessagesBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(messageRepository.countHighPriorityMessages()).thenReturn(0L);

        ApiResponse<MessageStats> response = service.getMessageStats("ghost");

        assertThat(response.isSuccess()).isTrue();
        MessageStats stats = response.getData();
        assertThat(stats.getSentCount()).isZero();
        assertThat(stats.getReceivedCount()).isZero();
        assertThat(stats.getUnreadCount()).isZero();
        assertThat(stats.getTotalCount()).isZero();
        assertThat(stats.getTodayCount()).isZero();
        assertThat(stats.getHighPriorityCount()).isZero();
    }

    @Test
    void getMessageStats_coercesIntegerAndBigIntegerCounts() {
        // Some JDBC drivers surface COUNT(...) as Integer or BigInteger rather than Long.
        when(messageRepository.getMessageStats(eq("ada")))
                .thenReturn(new Object[] {5, BigInteger.valueOf(7), 2L});
        Message today = Message.builder().id(1L).message("hi").build();
        when(messageRepository.findMessagesBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(today));
        when(messageRepository.countHighPriorityMessages()).thenReturn(3L);

        ApiResponse<MessageStats> response = service.getMessageStats("ada");

        assertThat(response.isSuccess()).isTrue();
        MessageStats stats = response.getData();
        assertThat(stats.getSentCount()).isEqualTo(5L);
        assertThat(stats.getReceivedCount()).isEqualTo(7L);
        assertThat(stats.getUnreadCount()).isEqualTo(2L);
        assertThat(stats.getTotalCount()).isEqualTo(12L);
        assertThat(stats.getTodayCount()).isEqualTo(1L);
        assertThat(stats.getHighPriorityCount()).isEqualTo(3L);
    }
}
