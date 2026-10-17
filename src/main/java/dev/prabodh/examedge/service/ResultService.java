package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.Views.AttemptResultView;
import dev.prabodh.examedge.dto.Views.AttemptSummary;
import dev.prabodh.examedge.dto.Views.LeaderboardRow;
import dev.prabodh.examedge.dto.Views.ReviewItem;
import dev.prabodh.examedge.dto.Views.ReviewOption;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptAnswer;
import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.ProctoringEvent;
import dev.prabodh.examedge.entity.ProctoringEventType;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.QuestionOption;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.ResourceNotFoundException;
import dev.prabodh.examedge.repository.AttemptAnswerRepository;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.ProctoringEventRepository;
import dev.prabodh.examedge.util.CsvLines;
import dev.prabodh.examedge.util.PageRequests;
import dev.prabodh.examedge.util.TimeFormats;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResultService {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int LEADERBOARD_SIZE = 10;

    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final ProctoringEventRepository proctoringEventRepository;
    private final GradingService gradingService;
    private final ExamService examService;
    private final AttemptService attemptService;

    public ResultService(AttemptRepository attemptRepository, AttemptAnswerRepository answerRepository,
                         ProctoringEventRepository proctoringEventRepository, GradingService gradingService,
                         ExamService examService, AttemptService attemptService) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.proctoringEventRepository = proctoringEventRepository;
        this.gradingService = gradingService;
        this.examService = examService;
        this.attemptService = attemptService;
    }

    public AttemptSummary summarize(Attempt attempt) {
        Exam exam = attempt.getExam();
        long seconds = attempt.timeTakenSeconds();
        return new AttemptSummary(attempt.getId(), exam.getId(), exam.getTitle(),
                attempt.getStudent().getFullName(), attempt.getStudent().getEmail(), attempt.getScore(),
                exam.getTotalMarks(), gradingService.percentage(attempt.getScore(), exam.getTotalMarks()),
                gradingService.isPassed(attempt.getScore(), exam.getTotalMarks(), exam.getPassingPercentage()),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getSubmittedAt(), seconds,
                TimeFormats.duration(seconds));
    }

    public Page<AttemptSummary> searchExamResults(Long examId, Long teacherId, String term, int page) {
        examService.getOwned(examId, teacherId);
        String pattern = "%" + PageRequests.normalizeSearch(term).toLowerCase() + "%";
        return attemptRepository.searchCompletedByExam(examId, pattern,
                PageRequests.of(page, Sort.by(Sort.Direction.DESC, "submittedAt"))).map(this::summarize);
    }

    public Page<AttemptSummary> historyFor(Long studentId, int page) {
        return attemptRepository.findByStudentIdAndStatusNot(studentId, AttemptStatus.IN_PROGRESS,
                PageRequests.of(page, Sort.by(Sort.Direction.DESC, "submittedAt"))).map(this::summarize);
    }

    public String exportResultsCsv(Long examId, Long teacherId) {
        examService.getOwned(examId, teacherId);
        List<Attempt> attempts = new ArrayList<>(attemptRepository.findByExamIdAndStatusNot(examId, AttemptStatus.IN_PROGRESS));
        attempts.sort(Comparator.comparing(Attempt::getSubmittedAt));
        StringBuilder csv = new StringBuilder("Student,Email,Score,Total Marks,Percentage,Result,Status,Started At,Submitted At,Time Taken Seconds\n");
        for (Attempt attempt : attempts) {
            AttemptSummary row = summarize(attempt);
            csv.append(CsvLines.escape(row.studentName())).append(',')
                    .append(CsvLines.escape(row.studentEmail())).append(',')
                    .append(row.score()).append(',')
                    .append(row.totalMarks()).append(',')
                    .append(row.percentage()).append(',')
                    .append(row.passed() ? "PASS" : "FAIL").append(',')
                    .append(row.status()).append(',')
                    .append(CSV_TIME.format(row.startedAt())).append(',')
                    .append(CSV_TIME.format(row.submittedAt())).append(',')
                    .append(row.timeTakenSeconds()).append('\n');
        }
        return csv.toString();
    }

    public AttemptResultView resultForStudent(Long attemptId, Long studentId) {
        Attempt attempt = attemptService.ownedAttempt(attemptId, studentId);
        if (attempt.isInProgress()) {
            throw new BusinessRuleException("This attempt is still in progress.");
        }
        return buildResult(attempt);
    }

    public AttemptResultView resultForTeacher(Long attemptId, Long teacherId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found."));
        if (!attempt.getExam().getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("Not the owner of this exam");
        }
        if (attempt.isInProgress()) {
            throw new BusinessRuleException("This attempt is still in progress.");
        }
        return buildResult(attempt);
    }

    public List<ProctoringEvent> eventsForTeacher(Long attemptId, Long teacherId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found."));
        if (!attempt.getExam().getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("Not the owner of this exam");
        }
        return proctoringEventRepository.findByAttemptIdOrderByOccurredAtAsc(attemptId);
    }

    private AttemptResultView buildResult(Attempt attempt) {
        Map<Long, AttemptAnswer> answers = answerRepository.findByAttemptIdWithSelection(attempt.getId())
                .stream().collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity()));
        List<ReviewItem> items = new ArrayList<>();
        int number = 1;
        for (Long questionId : AttemptService.parseOrder(attempt.getQuestionOrder())) {
            AttemptAnswer answer = answers.get(questionId);
            if (answer == null) {
                continue;
            }
            Question question = answer.getQuestion();
            Long chosenId = answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId();
            List<ReviewOption> options = new ArrayList<>();
            for (QuestionOption option : question.getOptions()) {
                options.add(new ReviewOption(option.getId(), option.getText(), option.isCorrect(),
                        option.getId().equals(chosenId)));
            }
            items.add(new ReviewItem(number++, question.getText(), question.getMarks(),
                    gradingService.awardedMarks(answer), chosenId != null, answer.isCorrect(),
                    answer.isFlaggedForReview(), options));
        }
        Set<ProctoringEventType> violationTypes = new HashSet<>(EnumSet.of(ProctoringEventType.TAB_SWITCH,
                ProctoringEventType.WINDOW_BLUR));
        long violations = proctoringEventRepository.countByAttemptIdAndEventTypeIn(attempt.getId(), violationTypes);
        return new AttemptResultView(summarize(attempt), attempt.getExam().getPassingPercentage(), violations, items);
    }

    public boolean hasCompleted(Long examId, Long studentId) {
        return attemptRepository.countByExamIdAndStudentIdAndStatusNot(examId, studentId, AttemptStatus.IN_PROGRESS) > 0;
    }

    public List<LeaderboardRow> leaderboard(Long examId, Long viewerStudentId) {
        List<Attempt> attempts = new ArrayList<>(attemptRepository.findByExamIdAndStatusNot(examId, AttemptStatus.IN_PROGRESS));
        attempts.sort(Comparator.comparingInt(Attempt::getScore).reversed()
                .thenComparingLong(Attempt::timeTakenSeconds));
        Set<Long> seenStudents = new HashSet<>();
        List<LeaderboardRow> rows = new ArrayList<>();
        for (Attempt attempt : attempts) {
            if (!seenStudents.add(attempt.getStudent().getId())) {
                continue;
            }
            Exam exam = attempt.getExam();
            rows.add(new LeaderboardRow(rows.size() + 1, attempt.getStudent().getFullName(), attempt.getScore(),
                    exam.getTotalMarks(), gradingService.percentage(attempt.getScore(), exam.getTotalMarks()),
                    TimeFormats.duration(attempt.timeTakenSeconds()),
                    attempt.getStudent().getId().equals(viewerStudentId)));
            if (rows.size() == LEADERBOARD_SIZE) {
                break;
            }
        }
        return rows;
    }
}
