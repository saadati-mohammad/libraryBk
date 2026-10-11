package ir.iau.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveInputFormPolicyRequest(
        @NotEmpty @Size(max = 12) List<@NotBlank @Size(max = 80) String> allowedSources,
        @NotBlank @Size(max = 80) String defaultSource,
        @Size(max = 120) String station,
        @Size(max = 80) String ownerUsername) {
}
