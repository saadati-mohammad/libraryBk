package ir.iau.library.dto;

import ir.iau.library.entity.InputDevice;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InputDeviceDto(long id, String name, String typeKey, String ownerUsername, String station,
                             Map<String, Object> config, List<String> capabilities, boolean enabled, boolean revoked,
                             Instant createdAt, Instant updatedAt, Instant lastSeenAt, long version) {
    public static InputDeviceDto from(InputDevice device, Map<String, Object> config, List<String> capabilities) {
        return new InputDeviceDto(device.getId(), device.getName(), device.getTypeKey(), device.getOwnerUsername(),
                device.getStation(), config, capabilities, device.isEnabled(), device.getRevokedAt() != null,
                device.getCreatedAt(), device.getUpdatedAt(), device.getLastSeenAt(), device.getVersion());
    }
}
