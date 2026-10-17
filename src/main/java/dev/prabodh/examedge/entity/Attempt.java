package dev.prabodh.examedge.entity;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_attempts_single_active",
                columnNames = {"exam_id", "student_id", "active_marker"}),
        indexes = {
                @Index(name = "idx_attempts_exam_status", columnList = "exam_id, status"),
                @Index(name = "idx_attempts_student", columnList = "student_id, status"),
                @Index(name = "idx_attempts_started_at", columnList = "started_at")
        })
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "question_order", nullable = false, length = 4000)
    private String questionOrder;

    @Column(name = "active_marker")
    private Integer activeMarker = 1;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.REMOVE)
    private List<AttemptAnswer> answers = new ArrayList<>();

    public boolean isInProgress() {
        return status == AttemptStatus.IN_PROGRESS;
    }

    public long timeTakenSeconds() {
        if (submittedAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(startedAt, submittedAt).getSeconds());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Exam getExam() { return exam; }
    public void setExam(Exam exam) { this.exam = exam; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus status) { this.status = status; }
    public String getQuestionOrder() { return questionOrder; }
    public void setQuestionOrder(String questionOrder) { this.questionOrder = questionOrder; }
    public Integer getActiveMarker() { return activeMarker; }
    public void setActiveMarker(Integer activeMarker) { this.activeMarker = activeMarker; }
    public List<AttemptAnswer> getAnswers() { return answers; }
    public void setAnswers(List<AttemptAnswer> answers) { this.answers = answers; }
}
