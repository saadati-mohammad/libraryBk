package ir.iau.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.dto.InputSourceTypeDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Discovers Spring-provided source types; extending input methods does not require core edits. */
@Component
public class InputSourceProviderRegistry {
    private final ObjectMapper mapper;
    private final Map<String, InputSourceProvider> providers;

    public InputSourceProviderRegistry(ObjectMapper mapper, List<InputSourceProvider> providers) {
        this.mapper = mapper;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                InputSourceProvider::key, Function.identity(), (first, duplicate) -> {
                    throw new IllegalStateException("Duplicate input source provider key: " + first.key());
                }));
    }

    public InputSourceProvider require(String key) {
        InputSourceProvider provider = providers.get(key);
        if (provider == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported input source type");
        return provider;
    }

    public List<InputSourceTypeDto> list() {
        return providers.values().stream().sorted(Comparator.comparing(InputSourceProvider::key))
                .map(provider -> new InputSourceTypeDto(provider.key(), provider.displayName(),
                        parseSchema(provider.schemaJson()), List.copyOf(provider.capabilities())))
                .toList();
    }

    private Map<String, Object> parseSchema(String schema) {
        try {
            return mapper.readValue(schema, new TypeReference<>() { });
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid schema from input source provider", error);
        }
    }
}
