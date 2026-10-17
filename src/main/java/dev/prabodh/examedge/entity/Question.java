package dev.prabodh.examedge.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions",
        indexes = @Index(name = "idx_questions_exam_order", columnList = "exam_id, display_order"))
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "question_text", nullable = false, length = 2000)
    private String text;

    @Column(nullable = false)
    private int marks = 1;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuestionOption> options = new ArrayList<>();

    public QuestionOption correctOption() {
        return options.stream().filter(QuestionOption::isCorrect).findFirst().orElse(null);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Exam getExam() { return exam; }
    public void setExam(Exam exam) { this.exam = exam; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getMarks() { return marks; }
    public void setMarks(int marks) { this.marks = marks; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public List<QuestionOption> getOptions() { return options; }
    public void setOptions(List<QuestionOption> options) { this.options = options; }
}
