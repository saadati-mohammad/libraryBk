package ir.iau.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Explicit scheduler wiring.
 *
 * <p>The context contains several {@link TaskScheduler} beans (Spring Boot's
 * auto-configured one, the WebSocket broker's internal executors, and the broker's
 * dedicated heartbeat scheduler). Without an explicit {@link Primary} bean, Spring's
 * {@code TaskSchedulerRouter} logs a warning and the {@code @Scheduled} jobs
 * ({@link FileCleanupScheduler}, reservation expiry) could land on an unintended
 * scheduler. This class makes the choice deterministic.
 */
@Configuration
public class SchedulingConfig {

    /**
     * The scheduler used for all {@code @Scheduled} methods in the application.
     */
    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("app-scheduler-");
        // Let Spring manage the lifecycle (initialize/shutdown) rather than calling
        // initialize() here, so the bean participates in graceful shutdown.
        return scheduler;
    }

    /**
     * Dedicated scheduler for STOMP broker heartbeats. Kept separate from the
     * application scheduler so slow heartbeats can never delay business jobs
     * (and vice versa). Injected into {@link WebSocketConfig} by qualifier.
     */
    @Bean(name = "websocketHeartbeatScheduler")
    public TaskScheduler websocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
