package ir.iau.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.dto.InputFormPolicyDto;
import ir.iau.library.dto.SaveInputFormPolicyRequest;
import ir.iau.library.entity.InputDevice;
import ir.iau.library.entity.InputFormPolicy;
import ir.iau.library.repository.InputDeviceRepository;
import ir.iau.library.repository.InputFormPolicyRepository;
import ir.iau.library.repository.StaffAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class InputFormPolicyService {
    private static final Pattern FORM_KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private final InputFormPolicyRepository policies;
    private final InputDeviceRepository devices;
    private final StaffAccountRepository staffAccounts;
    private final InputSourceProviderRegistry providers;
    private final ObjectMapper mapper;

    public InputFormPolicyService(InputFormPolicyRepository policies, InputDeviceRepository devices,
                                  StaffAccountRepository staffAccounts,
                                  InputSourceProviderRegistry providers, ObjectMapper mapper) {
        this.policies = policies;
        this.devices = devices;
        this.staffAccounts = staffAccounts;
        this.providers = providers;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public InputFormPolicyDto effective(String formKey, String station, String username) {
        validateFormKey(formKey);
        String stationKey = normalizeStation(station);
        InputFormPolicy policy = find(formKey, username, stationKey)
                .or(() -> find(formKey, username, ""))
                .or(() -> find(formKey, InputFormPolicy.GLOBAL_OWNER, stationKey))
                .or(() -> find(formKey, InputFormPolicy.GLOBAL_OWNER, ""))
                .orElse(null);
        if (policy == null) return new InputFormPolicyDto(formKey, stationKey,
                List.of("MANUAL", "KEYBOARD_WEDGE", "PHONE_CAMERA"), "MANUAL",
                "system-default", null, null, 0);
        return toDto(policy);
    }

    @Transactional(readOnly = true)
    public InputFormPolicyDto effectiveAdmin(String formKey, String station, String ownerUsername) {
        String owner = ownerUsername == null || ownerUsername.isBlank()
                ? InputFormPolicy.GLOBAL_OWNER
                : staffAccounts.findByUsernameIgnoreCase(ownerUsername.trim())
                        .map(account -> account.getUsername())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Policy owner must be an existing staff account"));
        return effective(formKey, station, owner);
    }

    @Transactional
    public InputFormPolicyDto savePersonal(String formKey, SaveInputFormPolicyRequest request, String username) {
        return save(formKey, request, username, username);
    }

    @Transactional
    public InputFormPolicyDto saveAdmin(String formKey, SaveInputFormPolicyRequest request, String actor) {
        String owner = request.ownerUsername() == null || request.ownerUsername().isBlank()
                ? InputFormPolicy.GLOBAL_OWNER : request.ownerUsername().trim();
        if (!InputFormPolicy.GLOBAL_OWNER.equals(owner)) {
            owner = staffAccounts.findByUsernameIgnoreCase(owner)
                    .map(account -> account.getUsername())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Policy owner must be an existing staff account"));
        }
        return save(formKey, request, owner, actor);
    }

    private InputFormPolicyDto save(String formKey, SaveInputFormPolicyRequest request, String owner, String actor) {
        validateFormKey(formKey);
        String station = normalizeStation(request.station());
        List<String> allowed = List.copyOf(new LinkedHashSet<>(request.allowedSources()));
        if (allowed.size() != request.allowedSources().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed input sources must not contain duplicates");
        }
        allowed.forEach(source -> validateSource(source, owner, station));
        String defaultSource = request.defaultSource().trim();
        if (!allowed.contains(defaultSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default source must be one of the allowed sources");
        }
        InputFormPolicy policy = policies.findByFormKeyAndOwnerUsernameAndStation(formKey, owner, station)
                .orElseGet(InputFormPolicy::new);
        policy.setFormKey(formKey);
        policy.setOwnerUsername(owner);
        policy.setStation(station);
        try {
            policy.setAllowedSourcesJson(mapper.writeValueAsString(allowed));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize input policy", error);
        }
        policy.setDefaultSource(defaultSource);
        policy.setUpdatedBy(actor);
        policy.setUpdatedAt(Instant.now());
        return toDto(policies.saveAndFlush(policy));
    }

    private void validateSource(String source, String owner, String station) {
        if (source.startsWith("DEVICE:")) {
            long id;
            try { id = Long.parseLong(source.substring("DEVICE:".length())); }
            catch (NumberFormatException error) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid device input source");
            }
            InputDevice device = devices.findById(id)
                    .filter(InputDevice::isEnabled)
                    .filter(deviceRow -> deviceRow.getRevokedAt() == null)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected device is unavailable"));
            if (!InputFormPolicy.GLOBAL_OWNER.equals(owner)
                    && device.getOwnerUsername() != null && !owner.equals(device.getOwnerUsername())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selected device belongs to another user");
            }
            if (!station.isBlank() && device.getStation() != null && !device.getStation().isBlank()
                    && !station.equals(device.getStation())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected device is assigned to another station");
            }
            return;
        }
        providers.require(source);
    }

    private java.util.Optional<InputFormPolicy> find(String form, String owner, String station) {
        return policies.findByFormKeyAndOwnerUsernameAndStation(form, owner, station);
    }

    private InputFormPolicyDto toDto(InputFormPolicy policy) {
        try {
            List<String> sources = mapper.readValue(policy.getAllowedSourcesJson(), new TypeReference<>() { });
            return new InputFormPolicyDto(policy.getFormKey(), policy.getStation(), sources, policy.getDefaultSource(),
                    InputFormPolicy.GLOBAL_OWNER.equals(policy.getOwnerUsername()) ? "global" : "user",
                    policy.getUpdatedBy(), policy.getUpdatedAt(), policy.getVersion());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored input policy is invalid", error);
        }
    }

    private void validateFormKey(String formKey) {
        if (formKey == null || !FORM_KEY.matcher(formKey).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid form key");
        }
    }

    private String normalizeStation(String station) {
        return station == null ? "" : station.trim();
    }
}
