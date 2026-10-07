package ir.iau.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/messages/{id}}. The message id travels in the path, so the
 * body carries only the new text. Previously the controller bound the raw request body
 * as a {@code String}, which stored the literal JSON (e.g. {@code {"message":"hi"}}) as
 * the message content and left no room for validation.
 */
public record UpdateMessageRequest(
        @NotBlank(message = "متن پیام الزامی است")
        @Size(max = 5000, message = "متن پیام نمی‌تواند بیش از ۵۰۰۰ کاراکتر باشد")
        String message
) {
}
