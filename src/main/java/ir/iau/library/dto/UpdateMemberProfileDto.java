package ir.iau.library.dto;

import jakarta.validation.constraints.Size;

public record UpdateMemberProfileDto(@Size(max = 30) String phone) { }
