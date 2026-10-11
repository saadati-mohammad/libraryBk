package ir.iau.library.dto;

import java.util.List;
import java.util.Map;

public record InputSourceTypeDto(String key, String displayName, Map<String, Object> configSchema, List<String> capabilities) { }
