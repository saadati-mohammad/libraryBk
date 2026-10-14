package ir.iau.library.dto;

import java.time.Instant;
import java.util.Map;

public record InputDeviceAuditEventDto(long id, String actorUsername, String action, Map<String, Object> details,
                                       String remoteAddress, Instant occurredAt) { }
