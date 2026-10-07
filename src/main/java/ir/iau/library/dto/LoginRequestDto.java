package ir.iau.library.dto;

import jakarta.validation.constraints.NotBlank;

/** Credentials submitted to {@code POST /api/auth/login}. */
public record LoginRequestDto(
        @NotBlank(message = "نام کاربری الزامی است") String username,
        @NotBlank(message = "رمز عبور الزامی است") String password) {
}