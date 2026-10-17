package dev.prabodh.examedge.config;

import dev.prabodh.examedge.service.AttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttemptExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryJob.class);

    private final AttemptService attemptService;

    public AttemptExpiryJob(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void submitExpiredAttempts() {
        int closed = attemptService.autoSubmitExpiredAttempts();
        if (closed > 0) {
            log.info("Auto-submitted {} expired attempts", closed);
        }
    }
}
