package ir.iau.library.dto;

import java.time.Instant;

public record InputDeviceHeartbeatDto(long id, boolean enabled, Instant lastSeenAt) { }
