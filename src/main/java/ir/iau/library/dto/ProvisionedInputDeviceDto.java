package ir.iau.library.dto;

/** Contains the raw credential only on create/rotation; never return it from list/read APIs. */
public record ProvisionedInputDeviceDto(InputDeviceDto device, String token) { }
