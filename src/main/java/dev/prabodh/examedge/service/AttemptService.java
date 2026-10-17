package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.Views.ExamSessionView;
import dev.prabodh.examedge.dto.Views.OptionView;
import dev.prabodh.examedge.dto.Views.QuestionView;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptAnswer;
import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.ExamStatus;
import dev.prabodh.examedge.entity.ProctoringEvent;
import dev.prabodh.examedge.entity.ProctoringEventType;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.QuestionOption;
import dev.prabodh.examedge.entity.User;
import dev.prabodh.examedge.exception.AttemptClosedException;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.ResourceNotFoundException;
import dev.prabodh.examedge.repository.AttemptAnswerRepository;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.ProctoringEventRepository;
import dev.prabodh.examedge.repository.QuestionRepository;
import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.util.AttemptTimer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = BusinessRuleException.class)
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);

    private static final Set<ProctoringEventType> VIOLATION_TYPES = Arrays.stream(ProctoringEventType.values())
            .filter(ProctoringEventType::isViolation)
            .collect(Collectors.toUnmodifiableSet());

    public record SubmitOutcome(Attempt attempt, boolean lateRejected, boolean alreadyClosed) {
    }

    public record ProctoringOutcome(long violations, int maxViolations, boolean autoSubmitted) {
    }

    public record AnswerOutcome(long remainingSeconds) {
    }

    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ProctoringEventRepository proctoringEventRepository;
    private final GradingService gradingService;
    private final AttemptTimer timer;
    private final Clock clock;

    public AttemptService(AttemptRepository attemptRepository, AttemptAnswerRepository answerRepository,
                          ExamRepository examRepository, QuestionRepository questionRepository,
                          UserRepository userRepository, ProctoringEventRepository proctoringEventRepository,
                          GradingService gradingService, AttemptTimer timer, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.proctoringEventRepository = proctoringEventRepository;
        this.gradingService = gradingService;
        this.timer = timer;
        this.clock = clock;
    }

    public Attempt start(Long examId, Long studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (exam.statusAt(now) != ExamStatus.LIVE) {
            throw new BusinessRuleException("This exam is not open right now.");
        }
        Optional<Attempt> active = attemptRepository.findByExamIdAndStudentIdAndStatus(
                examId, studentId, AttemptStatus.IN_PROGRESS);
        if (active.isPresent()) {
            Attempt existing = active.get();
            if (!timer.isExpired(existing.getStartedAt(), exam.getDurationMinutes(), now)) {
                return existing;
            }
            finalizeAttempt(existing, AttemptStatus.AUTO_SUBMITTED,
                    timer.deadline(existing.getStartedAt(), exam.getDurationMinutes()));
        }
        long completed = attemptRepository.countByExamIdAndStudentIdAndStatusNot(
                examId, studentId, AttemptStatus.IN_PROGRESS);
        if (completed > 0 && !exam.isAllowRetake()) {
            throw new BusinessRuleException("You have already completed this exam.");
        }
        List<Question> questions = questionRepository.findByExamIdOrderByDisplayOrderAsc(examId);
        if (questions.isEmpty()) {
            throw new BusinessRuleException("This exam has no questions yet.");
        }
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        List<Long> order = questions.stream().map(Question::getId).collect(Collectors.toCollection(ArrayList::new));
        if (exam.isShuffleQuestions()) {
            Collections.shuffle(order, new Random());
        }
        Attempt attempt = new Attempt();
        attempt.setExam(exam);
        attempt.setStudent(student);
        attempt.setStartedAt(now);
        attempt.setQuestionOrder(order.stream().map(String::valueOf).collect(Collectors.joining(",")));
        Attempt saved = attemptRepository.saveAndFlush(attempt);
        List<AttemptAnswer> answers = new ArrayList<>();
        for (Question question : questions) {
            AttemptAnswer answer = new AttemptAnswer();
            answer.setAttempt(saved);
            answer.setQuestion(question);
            answers.add(answer);
        }
        answerRepository.saveAll(answers);
        log.info("Attempt {} started for exam {} by student {}", saved.getId(), examId, studentId);
        return saved;
    }

    public ExamSessionView buildSession(Long attemptId, Long studentId) {
        Attempt attempt = ownedAttempt(attemptId, studentId);
        requireActive(attempt);
        Exam exam = attempt.getExam();
        Map<Long, AttemptAnswer> answersByQuestion = answerRepository.findByAttemptIdWithSelection(attemptId)
                .stream().collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer));
        Map<Long, Question> questionsById = questionRepository.findByExamIdOrderByDisplayOrderAsc(exam.getId())
                .stream().collect(Collectors.toMap(Question::getId, question -> question));
        List<QuestionView> views = new ArrayList<>();
        int number = 1;
        for (Long questionId : parseOrder(attempt.getQuestionOrder())) {
            Question question = questionsById.get(questionId);
            AttemptAnswer answer = answersByQuestion.get(questionId);
            if (question == null || answer == null) {
                continue;
            }
            List<QuestionOption> options = new ArrayList<>(question.getOptions());
            if (exam.isShuffleQuestions()) {
                Collections.shuffle(options, new Random(attemptId * 1_000_003L + questionId));
            }
            List<OptionView> optionViews = options.stream()
                    .map(option -> new OptionView(option.getId(), option.getText())).toList();
            Long selected = answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId();
            views.add(new QuestionView(questionId, number++, question.getText(), question.getMarks(),
                    optionViews, selected, answer.isFlaggedForReview()));
        }
        return new ExamSessionView(attemptId, exam.getTitle(), remainingSeconds(attempt),
                (int) violationCount(attemptId), exam.getMaxViolations(), views);
    }

    public Map<String, Object> stateOf(Long attemptId, Long studentId) {
        Attempt attempt = ownedAttempt(attemptId, studentId);
        requireActive(attempt);
        Map<String, Object> answers = new HashMap<>();
        List<Long> flagged = new ArrayList<>();
        for (AttemptAnswer answer : answerRepository.findByAttemptIdWithSelection(attemptId)) {
            if (answer.getSelectedOption() != null) {
                answers.put(String.valueOf(answer.getQuestion().getId()), answer.getSelectedOption().getId());
            }
            if (answer.isFlaggedForReview()) {
                flagged.add(answer.getQuestion().getId());
            }
        }
        Map<String, Object> state = new HashMap<>();
        state.put("remainingSeconds", remainingSeconds(attempt));
        state.put("violations", violationCount(attemptId));
        state.put("maxViolations", attempt.getExam().getMaxViolations());
        state.put("answers", answers);
        state.put("flagged", flagged);
        return state;
    }

    public AnswerOutcome saveAnswer(Long attemptId, Long studentId, Long questionId, Long optionId) {
        Attempt attempt = ownedAttempt(attemptId, studentId);
        requireActive(attempt);
        AttemptAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not part of this attempt."));
        if (optionId == null) {
            answer.setSelectedOption(null);
        } else {
            QuestionOption chosen = answer.getQuestion().getOptions().stream()
                    .filter(option -> option.getId().equals(optionId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Option does not belong to the question."));
            answer.setSelectedOption(chosen);
        }
        answerRepository.save(answer);
        return new AnswerOutcome(remainingSeconds(attempt));
    }

    public void setFlag(Long attemptId, Long studentId, Long questionId, boolean flagged) {
        Attempt attempt = ownedAttempt(attemptId, studentId);
        requireActive(attempt);
        AttemptAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not part of this attempt."));
        answer.setFlaggedForReview(flagged);
        answerRepository.save(answer);
    }

    public ProctoringOutcome recordProctoringEvent(Long attemptId, Long studentId, ProctoringEventType type) {
        Attempt attempt = ownedAttempt(attemptId, studentId);
        int max = attempt.getExam().getMaxViolations();
        if (!attempt.isInProgress()) {
            return new ProctoringOutcome(violationCount(attemptId), max, true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (timer.isExpired(attempt.getStartedAt(), attempt.getExam().getDurationMinutes(), now)) {
            expire(attempt);
            return new ProctoringOutcome(violationCount(attemptId), max, true);
        }
        saveEvent(attempt, type, now);
        long violations = violationCount(attemptId);
        if (type.isViolation() && violations >= max) {
            saveEvent(attempt, ProctoringEventType.VIOLATION_LIMIT_REACHED, now);
            finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED, now);
            log.info("Attempt {} auto-submitted after reaching the violation limit", attemptId);
            return new ProctoringOutcome(violations, max, true);
        }
        return new ProctoringOutcome(violations, max, false);
    }

    public SubmitOutcome submit(Long attemptId, Long studentId, boolean automatic) {
        Attempt attempt = ownedAttempt(attemptId, studentId);
        if (!attempt.isInProgress()) {
            return new SubmitOutcome(attempt, false, true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Exam exam = attempt.getExam();
        if (!timer.isSubmissionAccepted(attempt.getStartedAt(), exam.getDurationMinutes(), now)) {
            expire(attempt);
            log.warn("Late submission rejected for attempt {}", attemptId);
            return new SubmitOutcome(attempt, true, false);
        }
        LocalDateTime deadline = timer.deadline(attempt.getStartedAt(), exam.getDurationMinutes());
        LocalDateTime submittedAt = now.isAfter(deadline) ? deadline : now;
        finalizeAttempt(attempt, automatic ? AttemptStatus.AUTO_SUBMITTED : AttemptStatus.SUBMITTED, submittedAt);
        log.info("Attempt {} submitted with status {}", attemptId, attempt.getStatus());
        return new SubmitOutcome(attempt, false, false);
    }

    public int autoSubmitExpiredAttempts() {
        LocalDateTime now = LocalDateTime.now(clock);
        int closed = 0;
        for (Attempt attempt : attemptRepository.findByStatus(AttemptStatus.IN_PROGRESS)) {
            if (timer.isExpired(attempt.getStartedAt(), attempt.getExam().getDurationMinutes(), now)) {
                expire(attempt);
                closed++;
            }
        }
        return closed;
    }

    public Attempt ownedAttempt(Long attemptId, Long studentId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found."));
        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new ResourceNotFoundException("Attempt not found.");
        }
        return attempt;
    }

    private void requireActive(Attempt attempt) {
        if (!attempt.isInProgress()) {
            throw new AttemptClosedException(attempt.getId(), "This attempt has already been submitted.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (timer.isExpired(attempt.getStartedAt(), attempt.getExam().getDurationMinutes(), now)) {
            expire(attempt);
            throw new AttemptClosedException(attempt.getId(), "Time is up. Your answers were submitted automatically.");
        }
    }

    private void expire(Attempt attempt) {
        finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED,
                timer.deadline(attempt.getStartedAt(), attempt.getExam().getDurationMinutes()));
    }

    private void finalizeAttempt(Attempt attempt, AttemptStatus status, LocalDateTime submittedAt) {
        List<AttemptAnswer> answers = answerRepository.findByAttemptIdWithSelection(attempt.getId());
        attempt.setScore(gradingService.grade(answers));
        attempt.setStatus(status);
        attempt.setSubmittedAt(submittedAt);
        attempt.setActiveMarker(null);
        attemptRepository.saveAndFlush(attempt);
    }

    private void saveEvent(Attempt attempt, ProctoringEventType type, LocalDateTime when) {
        ProctoringEvent event = new ProctoringEvent();
        event.setAttempt(attempt);
        event.setEventType(type);
        event.setOccurredAt(when);
        proctoringEventRepository.save(event);
        proctoringEventRepository.flush();
    }

    private long violationCount(Long attemptId) {
        return proctoringEventRepository.countByAttemptIdAndEventTypeIn(attemptId, VIOLATION_TYPES);
    }

    private long remainingSeconds(Attempt attempt) {
        return timer.remainingSeconds(attempt.getStartedAt(), attempt.getExam().getDurationMinutes(),
                LocalDateTime.now(clock));
    }

    public static List<Long> parseOrder(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).map(Long::valueOf).toList();
    }
}
