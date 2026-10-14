package ir.iau.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // خواندن تنظیمات از application.properties
    @Value("${app.cors.allowed-origins:}")
    private String[] allowedOriginsFromProps;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    // لیست fallback برای وقتی که properties خالی باشد
    private static final String[] DEFAULT_ALLOWED_ORIGINS = {
            "http://localhost:4200",            // توسعه محلی
            "http://localhost",                 // دیپلوی استاتیک
            "https://lms-iau.vercel.app",       // ورسل تولیدی
            "https://*.vercel.app",             // پیش‌نمایش‌های ورسل
            "https://*.onrender.com",           // دامنه‌های سرویس رندر
            "https://lms-iau.liara.run",        // لیارا پیش‌فرض
            "https://lms-iau-ac.liara.run"      // لیارا دامنه رزرو
    };

    private String[] resolveAllowedOrigins() {
        if (allowedOriginsFromProps == null || allowedOriginsFromProps.length == 0) {
            return DEFAULT_ALLOWED_ORIGINS;
        }
        String[] filtered = Arrays.stream(allowedOriginsFromProps)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return filtered.length > 0 ? filtered : DEFAULT_ALLOWED_ORIGINS;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] originsToUse = resolveAllowedOrigins();

        registry.addMapping("/api/**")
                .allowedOriginPatterns(originsToUse)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        String[] originsToUse = resolveAllowedOrigins();

        configuration.setAllowedOriginPatterns(List.of(originsToUse));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
