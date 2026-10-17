package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.Views.ExamAnalytics;
import dev.prabodh.examedge.dto.Views.QuestionAccuracy;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.repository.AttemptAnswerRepository;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.QuestionRepository;
import dev.prabodh.examedge.util.TimeFormats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int BUCKET_COUNT = 10;
    private static final int HIGHLIGHT_COUNT = 3;

    private final ExamService examService;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final GradingService gradingService;

    public AnalyticsService(ExamService examService, AttemptRepository attemptRepository,
                            AttemptAnswerRepository answerRepository, QuestionRepository questionRepository,
                            GradingService gradingService) {
        this.examService = examService;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.gradingService = gradingService;
    }

    public ExamAnalytics analyze(Long examId, Long teacherId) {
        Exam exam = examService.getOwned(examId, teacherId);
        List<Attempt> attempts = attemptRepository.findByExamIdAndStatusNot(examId, AttemptStatus.IN_PROGRESS);
        int total = exam.getTotalMarks();
        long count = attempts.size();

        double average = attempts.stream().mapToInt(Attempt::getScore).average().orElse(0);
        int highest = attempts.stream().mapToInt(Attempt::getScore).max().orElse(0);
        int lowest = attempts.stream().mapToInt(Attempt::getScore).min().orElse(0);
        long passed = attempts.stream()
                .filter(a -> gradingService.isPassed(a.getScore(), total, exam.getPassingPercentage())).count();
        double passRate = count == 0 ? 0 : Math.round(passed * 1000.0 / count) / 10.0;
        long averageSeconds = Math.round(attempts.stream().mapToLong(Attempt::timeTakenSeconds).average().orElse(0));

        long[] buckets = new long[BUCKET_COUNT];
        for (Attempt attempt : attempts) {
            double percentage = total <= 0 ? 0 : attempt.getScore() * 100.0 / total;
            int index = Math.min(BUCKET_COUNT - 1, (int) (percentage / (100.0 / BUCKET_COUNT)));
            buckets[index]++;
        }
        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (int i = 0; i < BUCKET_COUNT; i++) {
            int from = i * (100 / BUCKET_COUNT);
            int to = i == BUCKET_COUNT - 1 ? 100 : from + (100 / BUCKET_COUNT) - 1;
            labels.add(from + "-" + to + "%");
            counts.add(buckets[i]);
        }

        Map<Long, long[]> accuracyByQuestion = new HashMap<>();
        for (Object[] row : answerRepository.summarizeAccuracyByQuestion(examId)) {
            long answered = ((Number) row[1]).longValue();
            long correct = row[2] == null ? 0 : ((Number) row[2]).longValue();
            accuracyByQuestion.put(((Number) row[0]).longValue(), new long[]{answered, correct});
        }
        List<QuestionAccuracy> accuracy = new ArrayList<>();
        int number = 1;
        for (Question question : questionRepository.findByExamIdOrderByDisplayOrderAsc(examId)) {
            long[] values = accuracyByQuestion.getOrDefault(question.getId(), new long[]{0, 0});
            double percentage = values[0] == 0 ? 0 : Math.round(values[1] * 1000.0 / values[0]) / 10.0;
            accuracy.add(new QuestionAccuracy(number++, question.getText(), values[0], values[1], percentage));
        }
        List<QuestionAccuracy> answeredOnly = accuracy.stream().filter(q -> q.answered() > 0).toList();
        List<QuestionAccuracy> hardest = answeredOnly.stream()
                .sorted(Comparator.comparingDouble(QuestionAccuracy::accuracyPercentage))
                .limit(HIGHLIGHT_COUNT).toList();
        List<QuestionAccuracy> easiest = answeredOnly.stream()
                .sorted(Comparator.comparingDouble(QuestionAccuracy::accuracyPercentage).reversed())
                .limit(HIGHLIGHT_COUNT).toList();

        return new ExamAnalytics(count, Math.round(average * 10) / 10.0, highest, lowest, passRate,
                TimeFormats.duration(averageSeconds), total, labels, counts, accuracy, hardest, easiest);
    }
}
