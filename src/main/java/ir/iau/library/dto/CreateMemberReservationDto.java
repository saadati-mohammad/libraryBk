package ir.iau.library.dto;

import jakarta.validation.constraints.NotNull;

public record CreateMemberReservationDto(@NotNull Long bookId) { }
