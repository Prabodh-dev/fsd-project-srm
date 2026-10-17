package dev.prabodh.examedge.dto;

import dev.prabodh.examedge.entity.Exam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

public class ExamForm {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must be at most 150 characters")
    private String title;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 600, message = "Duration must be at most 600 minutes")
    private Integer durationMinutes = 30;

    @NotNull(message = "Start time is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    @NotNull(message = "Passing percentage is required")
    @Min(value = 0, message = "Passing percentage must be between 0 and 100")
    @Max(value = 100, message = "Passing percentage must be between 0 and 100")
    private Integer passingPercentage = 40;

    @NotNull(message = "Maximum violations is required")
    @Min(value = 1, message = "Maximum violations must be at least 1")
    @Max(value = 20, message = "Maximum violations must be at most 20")
    private Integer maxViolations = 3;

    private boolean shuffleQuestions;

    private boolean allowRetake;

    public static ExamForm defaults() {
        ExamForm form = new ExamForm();
        LocalDateTime start = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.HOURS);
        form.setStartTime(start);
        form.setEndTime(start.plusDays(7));
        return form;
    }

    public static ExamForm from(Exam exam) {
        ExamForm form = new ExamForm();
        form.setTitle(exam.getTitle());
        form.setDescription(exam.getDescription());
        form.setDurationMinutes(exam.getDurationMinutes());
        form.setStartTime(exam.getStartTime());
        form.setEndTime(exam.getEndTime());
        form.setPassingPercentage(exam.getPassingPercentage());
        form.setMaxViolations(exam.getMaxViolations());
        form.setShuffleQuestions(exam.isShuffleQuestions());
        form.setAllowRetake(exam.isAllowRetake());
        return form;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getPassingPercentage() { return passingPercentage; }
    public void setPassingPercentage(Integer passingPercentage) { this.passingPercentage = passingPercentage; }
    public Integer getMaxViolations() { return maxViolations; }
    public void setMaxViolations(Integer maxViolations) { this.maxViolations = maxViolations; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public void setShuffleQuestions(boolean shuffleQuestions) { this.shuffleQuestions = shuffleQuestions; }
    public boolean isAllowRetake() { return allowRetake; }
    public void setAllowRetake(boolean allowRetake) { this.allowRetake = allowRetake; }
}
