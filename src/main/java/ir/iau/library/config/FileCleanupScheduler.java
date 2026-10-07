package ir.iau.library.config;

import ir.iau.library.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic maintenance tasks.
 *
 * <p>{@link FileService#cleanupIncompleteUploads()} existed but was never invoked from
 * anywhere, so uploads left in UPLOADING/FAILED state (and their files on disk)
 * accumulated forever. This scheduler wires it up, guarded by
 * {@code app.cleanup.enabled} so it can be turned off in environments that do not
 * want background jobs (e.g. during tests).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = false)
public class FileCleanupScheduler {

    private final FileService fileService;

    /**
     * Runs on the cron configured by {@code app.cleanup.cron} (default: daily at 02:00).
     * Spring's 6-field CronExpression syntax (second minute hour day month day-of-week).
     */
    @Scheduled(cron = "${app.cleanup.cron:0 0 2 * * *}")
    public void cleanupIncompleteUploads() {
        try {
            fileService.cleanupIncompleteUploads();
        } catch (Exception e) {
            // A failure here must never kill the scheduler thread.
            log.error("Scheduled file cleanup failed: {}", e.getMessage(), e);
        }
    }
}
