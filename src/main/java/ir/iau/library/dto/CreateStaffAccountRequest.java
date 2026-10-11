package ir.iau.library.dto;

import ir.iau.library.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffAccountRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._@+-]{2,79}") String username,
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotNull StaffRole role) { }
