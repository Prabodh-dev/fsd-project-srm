package dev.prabodh.examedge.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "attempt_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_attempt_answers_attempt_question",
                columnNames = {"attempt_id", "question_id"}),
        indexes = @Index(name = "idx_attempt_answers_question", columnList = "question_id"))
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    @Column(name = "flagged_for_review", nullable = false)
    private boolean flaggedForReview;

    public boolean isCorrect() {
        return selectedOption != null && selectedOption.isCorrect();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public QuestionOption getSelectedOption() { return selectedOption; }
    public void setSelectedOption(QuestionOption selectedOption) { this.selectedOption = selectedOption; }
    public boolean isFlaggedForReview() { return flaggedForReview; }
    public void setFlaggedForReview(boolean flaggedForReview) { this.flaggedForReview = flaggedForReview; }
}
