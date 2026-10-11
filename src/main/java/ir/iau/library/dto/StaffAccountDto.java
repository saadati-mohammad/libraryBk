package ir.iau.library.dto;

import ir.iau.library.entity.StaffAccount;

import java.time.Instant;

public record StaffAccountDto(Long id, String username, String displayName, String email, String role,
                              boolean active, Instant createdAt, Instant updatedAt) {
    public static StaffAccountDto from(StaffAccount account) {
        return new StaffAccountDto(account.getId(), account.getUsername(), account.getDisplayName(), account.getEmail(),
                account.getRole().name(), account.isActive(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
