package ir.iau.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateInputDeviceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String typeKey,
        @Size(max = 80) String ownerUsername,
        @Size(max = 120) String station,
        Map<String, Object> config) { }
