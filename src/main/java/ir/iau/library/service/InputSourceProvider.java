package ir.iau.library.service;

import java.util.List;
import java.util.Map;

/** Pluggable description and validation contract for a form input source. */
public interface InputSourceProvider {
    String key();
    String displayName();
    String schemaJson();
    List<String> capabilities();
    void validateConfig(Map<String, Object> config);
}
