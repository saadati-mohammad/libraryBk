package ir.iau.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.dto.CreateInputDeviceRequest;
import ir.iau.library.dto.InputDeviceAuditEventDto;
import ir.iau.library.dto.InputDeviceDto;
import ir.iau.library.dto.InputDeviceHeartbeatDto;
import ir.iau.library.dto.InputSourceTypeDto;
import ir.iau.library.dto.ProvisionedInputDeviceDto;
import ir.iau.library.dto.UpdateInputDeviceRequest;
import ir.iau.library.entity.InputDevice;
import ir.iau.library.entity.InputDeviceAuditEvent;
import ir.iau.library.repository.InputDeviceAuditEventRepository;
import ir.iau.library.repository.InputDeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class InputDeviceService {
    private static final int MAX_CONFIG_BYTES = 8_192;
    private static final Pattern SECRET_FIELD = Pattern.compile("(?i).*(secret|token|credential|password|api[-_]?key|private[-_]?key).*?$");
    private final InputDeviceRepository devices;
    private final InputDeviceAuditEventRepository events;
    private final InputSourceProviderRegistry providers;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public InputDeviceService(InputDeviceRepository devices, InputDeviceAuditEventRepository events,
                              InputSourceProviderRegistry providers, ObjectMapper mapper) {
        this.devices = devices;
        this.events = events;
        this.providers = providers;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<InputDeviceDto> list() {
        return devices.findAllByOrderByNameAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InputSourceTypeDto> listTypes() { return providers.list(); }

    @Transactional
    public ProvisionedInputDeviceDto create(CreateInputDeviceRequest request, String actor, String remoteAddress) {
        InputSourceProvider provider = providers.require(request.typeKey());
        String config = encodeConfig(request.config(), provider);
        InputDevice device = new InputDevice();
        device.setName(request.name().trim());
        device.setTypeKey(provider.key());
        device.setOwnerUsername(clean(request.ownerUsername()));
        device.setStation(clean(request.station()));
        device.setConfigJson(config);
        device.setCapabilitiesJson(writeJson(provider.capabilities()));
        device.setEnabled(true);
        String token = newToken();
        device.setTokenHash(hash(token));
        device = devices.saveAndFlush(device);
        appendEvent(device, actor, remoteAddress, "CREATED", Map.of("after", summary(device)));
        return new ProvisionedInputDeviceDto(toDto(device), token);
    }

    @Transactional(readOnly = true)
    public InputDeviceDto get(long id) { return toDto(requireDevice(id)); }

    @Transactional
    public InputDeviceDto update(long id, UpdateInputDeviceRequest request, String actor, String remoteAddress) {
        InputDevice device = requireMutableDevice(id);
        InputSourceProvider provider = providers.require(device.getTypeKey());
        Map<String, Object> before = summary(device);
        device.setName(request.name().trim());
        device.setOwnerUsername(clean(request.ownerUsername()));
        device.setStation(clean(request.station()));
        device.setConfigJson(encodeConfig(request.config(), provider));
        InputDevice saved = devices.saveAndFlush(device);
        appendEvent(saved, actor, remoteAddress, "UPDATED", Map.of("before", before, "after", summary(saved)));
        return toDto(saved);
    }

    @Transactional
    public InputDeviceDto setEnabled(long id, boolean enabled, String actor, String remoteAddress) {
        InputDevice device = requireMutableDevice(id);
        if (device.isEnabled() != enabled) {
            Map<String, Object> before = summary(device);
            device.setEnabled(enabled);
            InputDevice saved = devices.saveAndFlush(device);
            appendEvent(saved, actor, remoteAddress, enabled ? "ENABLED" : "DISABLED",
                    Map.of("before", before, "after", summary(saved)));
            return toDto(saved);
        }
        return toDto(device);
    }

    @Transactional
    public InputDeviceDto revoke(long id, String actor, String remoteAddress) {
        InputDevice device = requireDevice(id);
        if (device.getRevokedAt() == null) {
            Map<String, Object> before = summary(device);
            device.setEnabled(false);
            device.setTokenHash(null);
            device.setRevokedAt(Instant.now());
            InputDevice saved = devices.saveAndFlush(device);
            appendEvent(saved, actor, remoteAddress, "REVOKED", Map.of("before", before, "after", summary(saved)));
            return toDto(saved);
        }
        return toDto(device);
    }

    @Transactional
    public ProvisionedInputDeviceDto rotateToken(long id, String actor, String remoteAddress) {
        InputDevice device = requireMutableDevice(id);
        String token = newToken();
        device.setTokenHash(hash(token));
        InputDevice saved = devices.saveAndFlush(device);
        // The plaintext credential is deliberately absent from the audit detail.
        appendEvent(saved, actor, remoteAddress, "TOKEN_ROTATED", Map.of("tokenRotated", true));
        return new ProvisionedInputDeviceDto(toDto(saved), token);
    }

    @Transactional
    public InputDeviceHeartbeatDto heartbeat(long id, String bearerToken) {
        InputDevice device = requireDevice(id);
        if (!device.isEnabled() || device.getRevokedAt() != null || device.getTokenHash() == null
                || bearerToken == null || !MessageDigest.isEqual(device.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(bearerToken).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Device credential is invalid or revoked");
        }
        Instant now = Instant.now();
        if (device.getLastSeenAt() != null && device.getLastSeenAt().isAfter(now.minusSeconds(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Device heartbeat limit exceeded");
        }
        device.setLastSeenAt(now);
        InputDevice saved = devices.saveAndFlush(device);
        return new InputDeviceHeartbeatDto(saved.getId(), saved.isEnabled(), saved.getLastSeenAt());
    }

    /** Validates a registered camera device without heartbeat throttling (continuous scans can arrive rapidly). */
    @Transactional
    public void authenticatePhoneScanner(long id, String bearerToken) {
        InputDevice device = requireDevice(id);
        if (!"PHONE_CAMERA".equals(device.getTypeKey()) || !device.isEnabled() || device.getRevokedAt() != null
                || device.getTokenHash() == null || bearerToken == null
                || !MessageDigest.isEqual(device.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(bearerToken).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Registered phone scanner credential is invalid or revoked");
        }
        device.setLastSeenAt(Instant.now());
        devices.save(device);
    }

    @Transactional(readOnly = true)
    public List<InputDeviceAuditEventDto> audit(long id) {
        requireDevice(id);
        return events.findAllByDeviceIdOrderByOccurredAtDescIdDesc(id).stream().map(event ->
                new InputDeviceAuditEventDto(event.getId(), event.getActorUsername(), event.getAction(),
                        parseConfig(event.getDetailsJson()), event.getRemoteAddress(), event.getOccurredAt())).toList();
    }

    private InputDevice requireDevice(long id) {
        return devices.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
    }

    private InputDevice requireMutableDevice(long id) {
        InputDevice device = requireDevice(id);
        if (device.getRevokedAt() != null) throw new ResponseStatusException(HttpStatus.GONE, "Device was revoked");
        return device;
    }

    private String encodeConfig(Map<String, Object> config, InputSourceProvider provider) {
        Map<String, Object> normalized = config == null ? Map.of() : config;
        provider.validateConfig(normalized);
        String encoded = writeJson(normalized);
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device configuration is too large");
        }
        return encoded;
    }

    private InputDeviceDto toDto(InputDevice device) {
        return InputDeviceDto.from(device, parseConfig(device.getConfigJson()), parseCapabilities(device.getCapabilitiesJson()));
    }

    private void appendEvent(InputDevice device, String actor, String remoteAddress, String action, Object details) {
        InputDeviceAuditEvent event = new InputDeviceAuditEvent();
        event.setDevice(device);
        event.setActorUsername(actor);
        event.setAction(action);
        event.setDetailsJson(writeJson(details));
        event.setRemoteAddress(cleanAddress(remoteAddress));
        event.setOccurredAt(Instant.now());
        events.save(event);
    }

    private Map<String, Object> summary(InputDevice device) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", device.getName());
        summary.put("typeKey", device.getTypeKey());
        summary.put("ownerUsername", device.getOwnerUsername());
        summary.put("station", device.getStation());
        summary.put("enabled", device.isEnabled());
        summary.put("revoked", device.getRevokedAt() != null);
        summary.put("config", redactConfig(parseConfig(device.getConfigJson())));
        return summary;
    }

    private Map<String, Object> redactConfig(Map<String, Object> config) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        config.forEach((key, value) -> redacted.put(key,
                SECRET_FIELD.matcher(key).matches() ? "[REDACTED]" : redactNested(value)));
        return redacted;
    }

    private Object redactNested(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String field = String.valueOf(key);
                nested.put(field, SECRET_FIELD.matcher(field).matches() ? "[REDACTED]" : redactNested(item));
            });
            return nested;
        }
        if (value instanceof List<?> list) return list.stream().map(this::redactNested).toList();
        return value;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode device JSON", error);
        }
    }

    private Map<String, Object> parseConfig(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored device JSON is invalid", error);
        }
    }

    private List<String> parseCapabilities(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored device capabilities are invalid", error);
        }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String cleanAddress(String value) { return value == null || value.isBlank() ? null : value.substring(0, Math.min(64, value.length())); }
}
