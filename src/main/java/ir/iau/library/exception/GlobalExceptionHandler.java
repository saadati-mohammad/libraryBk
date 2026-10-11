package ir.iau.library.exception;

import ir.iau.library.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * مدیریت نبود بخش (part) الزامی در درخواست multipart.
     *
     * <p>مثلاً {@code POST /api/excel-import/books} بدون بدنهٔ فایل، یا {@code POST /api/book}
     * بدون بخش {@code book}. بدون این mapper، Spring استثنای زیرساختی پرتاب می‌کرد و پاسخ
     * به‌اشتباه ۵۰۰ می‌شد؛ در حالی که این یک خطای ورودی کاربر است.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestPart(
            MissingServletRequestPartException ex) {

        log.warn("Missing multipart part: {}", ex.getRequestPartName());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("بخش «" + ex.getRequestPartName() + "» در درخواست ارسال نشده است"));
    }

    /**
     * مدیریت درخواست multipart نامعتبر/ناقص (مثلاً بدنهٔ خالی یا Content-Type نادرست).
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipartException(MultipartException ex) {

        log.warn("Invalid multipart request: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("درخواست چندبخشی نامعتبر است؛ فایل یا داده ارسال نشده است"));
    }

    /**
     * مدیریت نوع رسانهٔ پشتیبانی‌نشده (مثلاً ارسال JSON به endpoint چندبخشی).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {

        log.warn("Unsupported media type: {}", ex.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("نوع محتوای درخواست پشتیبانی نمی‌شود"));
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
     * مسیرهای ناشناخته زیر /api/** در Spring Boot 3.5 به‌صورت NoResourceFoundException
     * پرتاب می‌شوند (نه NoHandlerFoundException). بدون این mapper، یک درخواست به مسیر ناموجود
     * به‌اشتباه ۵۰۰ بازمی‌گرداند و stack trace ثبت می‌کند؛ در حالی که پاسخ صحیح ۴۰۴ است.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(NoResourceFoundException ex) {

        log.warn("No resource found for: {}", ex.getResourcePath());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("صفحه یا سرویس مورد نظر یافت نشد"));
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
     * بدنهٔ درخواست JSON نامعتبر یا ناقص است ({@code HttpMessageNotReadableException}).
     * بدون این mapper، یک JSON خراب به‌عنوان خطای عمومی ۵۰۰ بازگردانده می‌شد.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {

        log.warn("Unreadable request body: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("بدنهٔ درخواست نامعتبر یا ناقص است"));
    }

    /**
     * پارامتر اجباری کوئری ارسال نشده است.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex) {

        log.warn("Missing required request parameter: {}", ex.getParameterName());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("پارامتر اجباری '" + ex.getParameterName() + "' ارسال نشده است"));
    }

    /**
     * نوع مقدار پارامتر (مسیر/کوئری) با نوع مورد انتظار هم‌خوان نیست.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        log.warn("Argument type mismatch for '{}': {}", ex.getName(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("مقدار پارامتر '" + ex.getName() + "' نامعتبر است"));
    }

    /**
     * متد HTTP برای این مسیر پشتیبانی نمی‌شود (مثلاً POST روی یک GET).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        log.warn("HTTP method not supported: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("متد درخواست برای این سرویس پشتیبانی نمی‌شود"));
    }

    /**
     * نقض محدودیت‌های پایگاه داده (کلید یکتا، کلید خارجی، فیلد NOT NULL).
     * متن خام پایگاه داده هرگز به کلاینت بازگردانده نمی‌شود.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("این عملیات با محدودیت‌های داده در تضاد است (مقدار تکراری یا وابستگی موجود)"));
    }

    /**
     * ویژگی مرتب‌سازی ناموجود در مدل (مثلاً {@code ?sort=doesNotExist}).
     * بدون این mapper، Spring Data یک خطای عمیق چارچوب پرتاب می‌کرد که به‌صورت ۵۰۰ دیده می‌شد.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Object>> handlePropertyReference(PropertyReferenceException ex) {

        log.warn("Unknown sort/filter property: {}", ex.getPropertyName());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("پارامتر مرتب‌سازی نامعتبر است: " + ex.getPropertyName()));
    }

    /**
     * عدم دسترسی کاربر احراز هویت‌شده به منبع (مثلاً پیام یا فایل کاربر دیگر).
     * {@code org.springframework.security.access.AccessDeniedException} زیرمجموعهٔ
     * RuntimeException است، بنابراین بدون این mapper به‌اشتباه ۵۰۰ بازگردانده می‌شد.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {

        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("شما مجاز به دسترسی به این منبع نیستید"));
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
