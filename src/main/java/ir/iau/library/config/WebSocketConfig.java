package ir.iau.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Comma-separated list of origins allowed to open a WebSocket connection.
     * Configured via {@code app.websocket.allowed-origins} so it can differ per environment.
     */
    @Value("${app.websocket.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    /**
     * در این متد، یک endpoint برای اتصال کلاینت‌های وب‌سوکت ثبت می‌شود.
     * این همان آدرسی است که کلاینت (فرانت‌اند) برای برقراری ارتباط اولیه استفاده می‌کند.
     *
     * @param registry رجیستری برای ثبت endpoint ها
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // endpoint اصلی برای اتصال وب‌سوکت.
        // آدرس '/ws-chat' با چیزی که در فایل environment.ts فرانت‌اند تعریف شده مطابقت دارد.
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addEndpoint("/ws-chat")
                // Origins come from configuration so dev/prod can differ safely.
                .setAllowedOriginPatterns(origins)
                // فعال‌سازی SockJS به عنوان یک جایگزین (fallback) برای مرورگرهایی
                // که از وب‌سوکت به صورت کامل پشتیبانی نمی‌کنند.
                .withSockJS();
    }

    /**
     * در این متد، یک message broker برای مسیریابی پیام‌ها پیکربندی می‌شود.
     * broker مسئول ارسال پیام‌ها به کلاینت‌های مشترک (subscribed) است.
     *
     * @param registry رجیستری برای پیکربندی broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // پیشوند مقصد برای پیام‌هایی که از سمت سرور به کلاینت‌ها ارسال می‌شوند (مانند چت روم‌ها، اعلان‌ها و...).
        // در پروژه شما، پیام‌ها به مقصدهایی مانند /topic/user/{username} ارسال می‌شوند.
        registry.enableSimpleBroker("/topic");

        // پیشوند مقصد برای پیام‌هایی که از کلاینت به سرور ارسال می‌شوند.
        // این پیشوند به متدهای با انوتیشن @MessageMapping در کنترلرها متصل می‌شود.
        // برای مثال، وقتی فرانت‌اند پیامی به /app/chat.send می‌فرستد، متد مربوطه در WebSocketController اجرا می‌شود.
        registry.setApplicationDestinationPrefixes("/app");
    }
}