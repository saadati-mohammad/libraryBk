package ir.iau.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateInputDeviceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String ownerUsername,
        @Size(max = 120) String station,
        Map<String, Object> config) { }
