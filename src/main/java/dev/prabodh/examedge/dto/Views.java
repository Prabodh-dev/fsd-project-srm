package dev.prabodh.examedge.dto;

import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.ExamStatus;
import java.time.LocalDateTime;
import java.util.List;

public final class Views {

    private Views() {
    }

    public record ExamSummary(Long id, String title, String description, String teacherName,
                              int durationMinutes, LocalDateTime startTime, LocalDateTime endTime,
                              int totalMarks, int passingPercentage, boolean published,
                              boolean shuffleQuestions, boolean allowRetake, int maxViolations,
                              ExamStatus status, long questionCount, long attemptCount) {
    }

    public record StudentExamCard(ExamSummary exam, Long activeAttemptId, boolean completed,
                                  boolean canStart) {
    }

    public record AttemptSummary(Long attemptId, Long examId, String examTitle, String studentName,
                                 String studentEmail, int score, int totalMarks, double percentage,
                                 boolean passed, AttemptStatus status, LocalDateTime startedAt,
                                 LocalDateTime submittedAt, long timeTakenSeconds, String timeTakenText) {
    }

    public record OptionView(Long id, String text) {
    }

    public record QuestionView(Long id, int number, String text, int marks, List<OptionView> options,
                               Long selectedOptionId, boolean flagged) {
    }

    public record ExamSessionView(Long attemptId, String examTitle, long remainingSeconds,
                                  int violationCount, int maxViolations, List<QuestionView> questions) {
    }

    public record ReviewOption(Long id, String text, boolean correct, boolean chosen) {
    }

    public record ReviewItem(int number, String questionText, int marks, int awarded, boolean answered,
                             boolean correct, boolean flagged, List<ReviewOption> options) {
    }

    public record AttemptResultView(AttemptSummary summary, int passingPercentage, long violationCount,
                                    List<ReviewItem> items) {
    }

    public record LeaderboardRow(int rank, String studentName, int score, int totalMarks,
                                 double percentage, String timeTakenText, boolean currentStudent) {
    }

    public record QuestionAccuracy(int number, String questionText, long answered, long correct,
                                   double accuracyPercentage) {
    }

    public record ExamAnalytics(long attemptCount, double averageScore, int highestScore, int lowestScore,
                                double passRate, String averageCompletionText, int totalMarks,
                                List<String> histogramLabels, List<Long> histogramCounts,
                                List<QuestionAccuracy> questionAccuracy, List<QuestionAccuracy> hardest,
                                List<QuestionAccuracy> easiest) {
    }

    public record AdminDashboard(long userCount, long examCount, long attemptCount,
                                 long studentCount, long teacherCount, String chartJson) {
    }

    public record StudentDashboard(List<StudentExamCard> live, List<StudentExamCard> upcoming,
                                   List<AttemptSummary> completed, double averagePercentage,
                                   double bestPercentage, long completedCount, String trendJson) {
    }
}
