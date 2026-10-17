package dev.prabodh.examedge.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exams",
        indexes = {
                @Index(name = "idx_exams_teacher", columnList = "teacher_id"),
                @Index(name = "idx_exams_published_window", columnList = "published, start_time, end_time")
        })
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "total_marks", nullable = false)
    private int totalMarks;

    @Column(name = "passing_percentage", nullable = false)
    private int passingPercentage;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "allow_retake", nullable = false)
    private boolean allowRetake;

    @Column(name = "max_violations", nullable = false)
    private int maxViolations = 3;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "exam", cascade = CascadeType.REMOVE)
    @OrderBy("displayOrder ASC")
    private List<Question> questions = new ArrayList<>();

    public ExamStatus statusAt(LocalDateTime moment) {
        if (!published) {
            return ExamStatus.DRAFT;
        }
        if (moment.isBefore(startTime)) {
            return ExamStatus.SCHEDULED;
        }
        if (moment.isAfter(endTime)) {
            return ExamStatus.ENDED;
        }
        return ExamStatus.LIVE;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public int getPassingPercentage() { return passingPercentage; }
    public void setPassingPercentage(int passingPercentage) { this.passingPercentage = passingPercentage; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public void setShuffleQuestions(boolean shuffleQuestions) { this.shuffleQuestions = shuffleQuestions; }
    public boolean isAllowRetake() { return allowRetake; }
    public void setAllowRetake(boolean allowRetake) { this.allowRetake = allowRetake; }
    public int getMaxViolations() { return maxViolations; }
    public void setMaxViolations(int maxViolations) { this.maxViolations = maxViolations; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
}
