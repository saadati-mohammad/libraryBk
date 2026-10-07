package ir.iau.library.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLoanRequestDto {

    @NotNull(message = "personId is required")
    private Long personId;

    @NotNull(message = "bookId is required")
    private Long bookId;

    @Size(max = 1000, message = "notes must be at most 1000 characters")
    private String notes;
}
