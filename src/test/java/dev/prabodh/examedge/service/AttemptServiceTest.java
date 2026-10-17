package dev.prabodh.examedge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.prabodh.examedge.dto.Views.ExamSessionView;
import dev.prabodh.examedge.dto.Views.QuestionView;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.ProctoringEventType;
import dev.prabodh.examedge.exception.AttemptClosedException;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.service.AttemptService.ProctoringOutcome;
import dev.prabodh.examedge.service.AttemptService.SubmitOutcome;
import dev.prabodh.examedge.support.IntegrationTest;
import dev.prabodh.examedge.support.MutableClock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AttemptServiceTest {

    @Autowired
    private AttemptService attemptService;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MutableClock clock;

    private Exam exam;
    private Long studentId;

    @BeforeEach
    void setUp() {
        clock.reset();
        exam = examRepository.findAll().get(0);
        studentId = userRepository.findByEmailIgnoreCase("student@examedge.com").orElseThrow().getId();
    }

    private void answerFirstOptionOfEveryQuestion(Attempt attempt) {
        ExamSessionView session = attemptService.buildSession(attempt.getId(), studentId);
        for (QuestionView question : session.questions()) {
            attemptService.saveAnswer(attempt.getId(), studentId, question.id(), question.options().get(0).id());
        }
    }

    @Test
    void startingTwiceReturnsTheSameActiveAttempt() {
        Attempt first = attemptService.start(exam.getId(), studentId);
        Attempt second = attemptService.start(exam.getId(), studentId);
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void questionOrderIsStableAcrossReloads() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        List<Long> firstLoad = attemptService.buildSession(attempt.getId(), studentId).questions()
                .stream().map(QuestionView::id).toList();
        List<Long> secondLoad = attemptService.buildSession(attempt.getId(), studentId).questions()
                .stream().map(QuestionView::id).toList();
        assertEquals(firstLoad, secondLoad);
        assertEquals(exam.getQuestions().size(), firstLoad.size());
    }

    @Test
    void answersSurviveReloadAndGradeOnSubmit() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        answerFirstOptionOfEveryQuestion(attempt);
        ExamSessionView reloaded = attemptService.buildSession(attempt.getId(), studentId);
        assertTrue(reloaded.questions().stream().allMatch(q -> q.selectedOptionId() != null));
        SubmitOutcome outcome = attemptService.submit(attempt.getId(), studentId, false);
        assertEquals(AttemptStatus.SUBMITTED, outcome.attempt().getStatus());
        assertFalse(outcome.lateRejected());
        assertTrue(outcome.attempt().getScore() >= 0);
        assertThrows(AttemptClosedException.class,
                () -> attemptService.saveAnswer(attempt.getId(), studentId,
                        reloaded.questions().get(0).id(), reloaded.questions().get(0).options().get(0).id()));
    }

    @Test
    void submissionWithinGraceIsAccepted() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        clock.advance(Duration.ofMinutes(exam.getDurationMinutes()).plusSeconds(4));
        SubmitOutcome outcome = attemptService.submit(attempt.getId(), studentId, true);
        assertFalse(outcome.lateRejected());
        assertEquals(AttemptStatus.AUTO_SUBMITTED, outcome.attempt().getStatus());
    }

    @Test
    void submissionAfterGraceIsRejectedButSavedAnswersAreGraded() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        answerFirstOptionOfEveryQuestion(attempt);
        clock.advance(Duration.ofMinutes(exam.getDurationMinutes()).plusSeconds(30));
        SubmitOutcome outcome = attemptService.submit(attempt.getId(), studentId, false);
        assertTrue(outcome.lateRejected());
        assertEquals(AttemptStatus.AUTO_SUBMITTED, outcome.attempt().getStatus());
        assertEquals(Duration.ofMinutes(exam.getDurationMinutes()).getSeconds(), outcome.attempt().timeTakenSeconds());
    }

    @Test
    void scheduledJobAutoSubmitsExpiredAttempts() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        clock.advance(Duration.ofMinutes(exam.getDurationMinutes()).plusMinutes(1));
        assertEquals(1, attemptService.autoSubmitExpiredAttempts());
        assertEquals(AttemptStatus.AUTO_SUBMITTED, attemptService.ownedAttempt(attempt.getId(), studentId).getStatus());
        assertEquals(0, attemptService.autoSubmitExpiredAttempts());
    }

    @Test
    void accessingAnExpiredAttemptSubmitsItAutomatically() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        clock.advance(Duration.ofMinutes(exam.getDurationMinutes()).plusMinutes(2));
        assertThrows(AttemptClosedException.class, () -> attemptService.buildSession(attempt.getId(), studentId));
        assertEquals(AttemptStatus.AUTO_SUBMITTED, attemptService.ownedAttempt(attempt.getId(), studentId).getStatus());
    }

    @Test
    void reachingViolationLimitAutoSubmits() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        ProctoringOutcome outcome = null;
        for (int i = 0; i < exam.getMaxViolations(); i++) {
            outcome = attemptService.recordProctoringEvent(attempt.getId(), studentId, ProctoringEventType.TAB_SWITCH);
        }
        assertTrue(outcome.autoSubmitted());
        assertEquals(exam.getMaxViolations(), outcome.violations());
        assertEquals(AttemptStatus.AUTO_SUBMITTED, attemptService.ownedAttempt(attempt.getId(), studentId).getStatus());
    }

    @Test
    void blockedClipboardEventsDoNotCountAsViolations() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        ProctoringOutcome outcome = attemptService.recordProctoringEvent(attempt.getId(), studentId,
                ProctoringEventType.COPY_BLOCKED);
        assertEquals(0, outcome.violations());
        assertFalse(outcome.autoSubmitted());
    }

    @Test
    void secondCompletedAttemptRequiresRetakePermission() {
        exam.setAllowRetake(false);
        examRepository.save(exam);
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        attemptService.submit(attempt.getId(), studentId, false);
        assertThrows(BusinessRuleException.class, () -> attemptService.start(exam.getId(), studentId));
        exam.setAllowRetake(true);
        examRepository.save(exam);
        Attempt retake = attemptService.start(exam.getId(), studentId);
        assertNotEquals(attempt.getId(), retake.getId());
    }

    @Test
    void attemptsOfOtherStudentsAreHidden() {
        Attempt attempt = attemptService.start(exam.getId(), studentId);
        Long teacherId = userRepository.findByEmailIgnoreCase("teacher@examedge.com").orElseThrow().getId();
        assertThrows(RuntimeException.class, () -> attemptService.buildSession(attempt.getId(), teacherId));
    }
}
