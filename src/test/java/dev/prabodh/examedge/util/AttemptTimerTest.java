package dev.prabodh.examedge.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AttemptTimerTest {

    private final AttemptTimer timer = new AttemptTimer(5);
    private final LocalDateTime startedAt = LocalDateTime.of(2030, 1, 1, 10, 0, 0);

    @Test
    void deadlineIsStartPlusDuration() {
        assertEquals(startedAt.plusMinutes(30), timer.deadline(startedAt, 30));
    }

    @Test
    void remainingSecondsCountDownFromServerTime() {
        assertEquals(1800, timer.remainingSeconds(startedAt, 30, startedAt));
        assertEquals(1500, timer.remainingSeconds(startedAt, 30, startedAt.plusMinutes(5)));
    }

    @Test
    void remainingSecondsNeverGoNegative() {
        assertEquals(0, timer.remainingSeconds(startedAt, 30, startedAt.plusMinutes(45)));
    }

    @Test
    void submissionAcceptedUntilDeadlinePlusGrace() {
        LocalDateTime deadline = startedAt.plusMinutes(30);
        assertTrue(timer.isSubmissionAccepted(startedAt, 30, deadline));
        assertTrue(timer.isSubmissionAccepted(startedAt, 30, deadline.plusSeconds(5)));
    }

    @Test
    void submissionRejectedAfterGraceWindow() {
        LocalDateTime deadline = startedAt.plusMinutes(30);
        assertFalse(timer.isSubmissionAccepted(startedAt, 30, deadline.plusSeconds(6)));
        assertTrue(timer.isExpired(startedAt, 30, deadline.plusSeconds(6)));
        assertFalse(timer.isExpired(startedAt, 30, deadline.plusSeconds(1)));
    }
}
