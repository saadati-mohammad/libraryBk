package ir.iau.library.dto;

import ir.iau.library.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStaffAccountRequest(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
        @NotNull StaffRole role,
        @Size(min = 12, max = 128) String password) { }
