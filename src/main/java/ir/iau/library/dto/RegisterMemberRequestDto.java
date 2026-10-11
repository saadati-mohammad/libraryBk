package ir.iau.library.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterMemberRequestDto(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[0-9]{10}", message = "کد ملی باید ۱۰ رقم باشد") String nationalId,
        @Size(max = 30) String phone,
        @NotBlank @Size(min = 12, max = 128, message = "گذرواژه باید بین ۱۲ تا ۱۲۸ نویسه باشد") String password) { }
