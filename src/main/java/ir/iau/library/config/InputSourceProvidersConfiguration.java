package ir.iau.library.config;

import ir.iau.library.service.InputSourceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class InputSourceProvidersConfiguration {
    private static final String OBJECT_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":%s}";

    @Bean
    InputSourceProvider keyboardWedgeInputSource() {
        return provider("KEYBOARD_WEDGE", "اسکنر USB / بلوتوث / Wi-Fi (Keyboard Wedge)",
                "{\"prefix\":{\"type\":\"string\",\"maxLength\":8},\"suffix\":{\"type\":\"string\",\"maxLength\":8},\"terminator\":{\"type\":\"string\",\"enum\":[\"ENTER\",\"TAB\",\"NONE\"]},\"minimumLength\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":128}}",
                List.of("keyboard-wedge", "usb", "bluetooth", "wifi", "configurable-terminator"),
                Set.of("prefix", "suffix", "terminator", "minimumLength"));
    }

    @Bean
    InputSourceProvider phoneCameraInputSource() {
        return provider("PHONE_CAMERA", "دوربین موبایل / PWA",
                "{\"cameraFacing\":{\"type\":\"string\",\"enum\":[\"environment\",\"user\"]},\"torchEnabled\":{\"type\":\"boolean\"}}",
                List.of("camera", "pwa", "torch-optional", "paired-session"), Set.of("cameraFacing", "torchEnabled"));
    }

    @Bean
    InputSourceProvider manualEntryInputSource() {
        return provider("MANUAL", "ورود دستی با صفحه‌کلید", "{}", List.of("keyboard"), Set.of());
    }

    private InputSourceProvider provider(String key, String label, String properties, List<String> capabilities,
                                         Set<String> allowedKeys) {
        return new InputSourceProvider() {
            @Override public String key() { return key; }
            @Override public String displayName() { return label; }
            @Override public String schemaJson() { return OBJECT_SCHEMA.formatted(properties); }
            @Override public List<String> capabilities() { return capabilities; }
            @Override public void validateConfig(Map<String, Object> config) {
                if (config == null || config.size() > allowedKeys.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device configuration must be a bounded object");
                }
                config.keySet().forEach(field -> {
                    if (!allowedKeys.contains(field)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported configuration field: " + field);
                    }
                });
                if ("KEYBOARD_WEDGE".equals(key)) {
                    validateText(config, "prefix");
                    validateText(config, "suffix");
                    String terminator = stringValue(config, "terminator", "ENTER");
                    if (!Set.of("ENTER", "TAB", "NONE").contains(terminator)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported scanner terminator");
                    }
                    int minimumLength = integerValue(config, "minimumLength", 1);
                    if (minimumLength < 1 || minimumLength > 128) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scanner minimum length");
                    }
                }
                if ("PHONE_CAMERA".equals(key)) {
                    String facing = stringValue(config, "cameraFacing", "environment");
                    if (!Set.of("environment", "user").contains(facing)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported camera facing mode");
                    }
                    if (config.containsKey("torchEnabled") && !(config.get("torchEnabled") instanceof Boolean)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "torchEnabled must be boolean");
                    }
                }
            }
        };
    }

    private static void validateText(Map<String, Object> config, String field) {
        if (!config.containsKey(field)) return;
        Object value = config.get(field);
        if (!(value instanceof String text) || text.length() > 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a string up to 8 characters");
        }
    }

    private static String stringValue(Map<String, Object> config, String field, String defaultValue) {
        Object value = config.get(field);
        if (value == null) return defaultValue;
        if (value instanceof String text) return text;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a string");
    }

    private static int integerValue(Map<String, Object> config, String field, int defaultValue) {
        Object value = config.get(field);
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.intValue();
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be an integer");
    }
}
