package dev.prabodh.examedge.dto;

import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.QuestionOption;
import dev.prabodh.examedge.util.QuestionDraft;
import java.util.ArrayList;
import java.util.List;

public class QuestionForm {

    private String text;
    private Integer marks = 1;
    private List<String> optionTexts = new ArrayList<>();
    private int correctIndex = -1;

    public QuestionForm() {
        for (int i = 0; i < QuestionDraft.MAX_OPTIONS; i++) {
            optionTexts.add("");
        }
    }

    public static QuestionForm from(Question question) {
        QuestionForm form = new QuestionForm();
        form.setText(question.getText());
        form.setMarks(question.getMarks());
        List<QuestionOption> options = question.getOptions();
        for (int i = 0; i < options.size() && i < QuestionDraft.MAX_OPTIONS; i++) {
            form.getOptionTexts().set(i, options.get(i).getText());
            if (options.get(i).isCorrect()) {
                form.setCorrectIndex(i);
            }
        }
        return form;
    }

    public QuestionDraft toDraft() {
        int safeMarks = marks == null ? 0 : marks;
        return QuestionDraft.fromRaw(text, safeMarks, optionTexts, correctIndex);
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getMarks() { return marks; }
    public void setMarks(Integer marks) { this.marks = marks; }
    public List<String> getOptionTexts() { return optionTexts; }
    public void setOptionTexts(List<String> optionTexts) { this.optionTexts = optionTexts; }
    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }
}
