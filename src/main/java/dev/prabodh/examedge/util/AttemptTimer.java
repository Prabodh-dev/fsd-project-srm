package dev.prabodh.examedge.util;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AttemptTimer {

    private final Duration submitGrace;

    public AttemptTimer(@Value("${examedge.exam.submit-grace-seconds:5}") long submitGraceSeconds) {
        this.submitGrace = Duration.ofSeconds(submitGraceSeconds);
    }

    public LocalDateTime deadline(LocalDateTime startedAt, int durationMinutes) {
        return startedAt.plusMinutes(durationMinutes);
    }

    public long remainingSeconds(LocalDateTime startedAt, int durationMinutes, LocalDateTime now) {
        long seconds = Duration.between(now, deadline(startedAt, durationMinutes)).getSeconds();
        return Math.max(0, seconds);
    }

    public boolean isSubmissionAccepted(LocalDateTime startedAt, int durationMinutes, LocalDateTime now) {
        LocalDateTime lastAcceptedMoment = deadline(startedAt, durationMinutes).plus(submitGrace);
        return !now.isAfter(lastAcceptedMoment);
    }

    public boolean isExpired(LocalDateTime startedAt, int durationMinutes, LocalDateTime now) {
        return !isSubmissionAccepted(startedAt, durationMinutes, now);
    }
}
