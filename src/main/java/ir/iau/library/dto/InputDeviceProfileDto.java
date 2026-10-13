package ir.iau.library.dto;

import java.util.Map;

/** Safe runtime configuration for a device explicitly allowed by an effective form policy. */
public record InputDeviceProfileDto(long deviceId, String name, String typeKey, Map<String, Object> config) { }
