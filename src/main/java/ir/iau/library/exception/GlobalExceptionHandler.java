package ir.iau.library.exception;

import ir.iau.library.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * مدیریت خطاهای اعتبارسنجی ورودی
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        log.warn("Validation error occurred: {}", ex.getMessage());

        List<String> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(error.getField() + ": " + error.getDefaultMessage());
        }

        String errorMessage = "خطای اعتبارسنجی: " + String.join(", ", errors);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorMessage));
    }

    /**
     * مدیریت خطاهای اعتبارسنجی Bean
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException ex) {

        log.warn("Bind error occurred: {}", ex.getMessage());

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        String errorMessage = "خطای اعتبارسنجی: " + String.join(", ", errors);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorMessage));
    }

    /**
     * مدیریت خطاهای constraint validation
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        log.warn("Constraint violation error: {}", ex.getMessage());

        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        String errorMessage = "خطای اعتبارسنجی: " + String.join(", ", errors);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorMessage));
    }

    /**
     * مدیریت خطای حجم فایل بیش از حد
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex) {

        log.warn("File size exceeded: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("حجم فایل بیش از حد مجاز است"));
    }

    /**
     * مدیریت خطای 404 - صفحه یافت نشد
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFound(
            NoHandlerFoundException ex) {

        log.warn("No handler found for: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("صفحه یا سرویس مورد نظر یافت نشد"));
    }

    /**
     * مدیریت خطاهای احراز هویت (ورود با نام کاربری/رمز نادرست).
     * بدون این mapper، BadCredentialsException به‌عنوان خطای عمومی ۵۰۰ بازگردانده می‌شد.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(AuthenticationException ex) {

        log.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("نام کاربری یا رمز عبور نادرست است"));
    }

    /**
     * مدیریت خطاهای دسترسی به فایل
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(SecurityException ex) {

        log.warn("Security exception: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("دسترسی غیرمجاز"));
    }

    /**
     * مدیریت خطاهای عمومی IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("پارامتر ورودی نامعتبر"));
    }

    /**
     * مدیریت خطاهای نقض قوانین کسب‌وکار (مثلاً امانت کتابی که در امانت است یا
     * عضوی که غیرفعال است). این خطاها تقاضای نامعتبر هستند، نه خطای سرور.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalStateException(
            IllegalStateException ex) {

        log.warn("Business rule violation: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * مدیریت موجودیت یافت‌نشده (مثلاً کتاب یا عضو ناموجود).
     */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleEntityNotFound(
            jakarta.persistence.EntityNotFoundException ex) {

        log.warn("Entity not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * مدیریت خطاهای عمومی RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {

        log.error("Runtime exception occurred: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("خطای داخلی سرور. لطفاً دوباره تلاش کنید"));
    }

    /**
     * مدیریت خطاهای عمومی Exception
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {

        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("خطای غیرمنتظره‌ای رخ داد. لطفاً با پشتیبانی تماس بگیرید"));
    }

    /**
     * خطاهای سفارشی مربوط به فایل
     */
    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handleFileProcessingException(
            FileProcessingException ex) {

        log.warn("File processing error: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("خطا در پردازش فایل"));
    }

    /**
     * خطاهای سفارشی مربوط به پیام
     */
    @ExceptionHandler(MessageProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handleMessageProcessingException(
            MessageProcessingException ex) {

        log.warn("Message processing error: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("خطا در پردازش پیام"));
    }
}

/**
 * کلاس‌های Exception سفارشی
 */
class FileProcessingException extends RuntimeException {
    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

class MessageProcessingException extends RuntimeException {
    public MessageProcessingException(String message) {
        super(message);
    }

    public MessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}