package ir.iau.library.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// معیارهای مکالمه
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationCriteria {
    private String senderUsername;
    private String recipientUsername;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 15;
}