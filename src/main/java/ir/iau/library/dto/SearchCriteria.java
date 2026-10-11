package ir.iau.library.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// معیارهای جستجو
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchCriteria {
    private String query;
    private String sender;
    private String recipient;
    private String subject;
    private String priority;
    private Boolean isActive;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    /** Authenticated caller: results are always restricted to messages this user participates in. */
    private String username;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 15;
}