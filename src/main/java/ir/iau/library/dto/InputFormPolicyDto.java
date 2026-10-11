package ir.iau.library.dto;

import java.time.Instant;
import java.util.List;

public record InputFormPolicyDto(String formKey, String station, List<String> allowedSources,
                                 String defaultSource, String scope, String updatedBy,
                                 Instant updatedAt, long version) {
}
