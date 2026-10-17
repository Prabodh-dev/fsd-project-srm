package dev.prabodh.examedge.util;

import java.util.ArrayList;
import java.util.List;

public record QuestionDraft(String text, int marks, List<String> options, int correctIndex) {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 6;
    public static final int MAX_MARKS = 100;
    public static final int MAX_TEXT_LENGTH = 2000;
    public static final int MAX_OPTION_LENGTH = 1000;

    public static QuestionDraft fromRaw(String text, int marks, List<String> rawOptions, int rawCorrectIndex) {
        List<String> compacted = new ArrayList<>();
        int compactedCorrectIndex = -1;
        for (int i = 0; i < rawOptions.size(); i++) {
            String option = rawOptions.get(i) == null ? "" : rawOptions.get(i).trim();
            if (option.isEmpty()) {
                continue;
            }
            if (i == rawCorrectIndex) {
                compactedCorrectIndex = compacted.size();
            }
            compacted.add(option);
        }
        String cleanedText = text == null ? "" : text.trim();
        return new QuestionDraft(cleanedText, marks, compacted, compactedCorrectIndex);
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (text.isEmpty()) {
            problems.add("Question text is required.");
        } else if (text.length() > MAX_TEXT_LENGTH) {
            problems.add("Question text must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        if (marks < 1 || marks > MAX_MARKS) {
            problems.add("Marks must be between 1 and " + MAX_MARKS + ".");
        }
        if (options.size() < MIN_OPTIONS) {
            problems.add("Provide at least " + MIN_OPTIONS + " options.");
        }
        if (options.size() > MAX_OPTIONS) {
            problems.add("Provide at most " + MAX_OPTIONS + " options.");
        }
        if (options.stream().anyMatch(option -> option.length() > MAX_OPTION_LENGTH)) {
            problems.add("Each option must be at most " + MAX_OPTION_LENGTH + " characters.");
        }
        if (correctIndex < 0 || correctIndex >= options.size()) {
            problems.add("Select exactly one correct option that is not empty.");
        }
        return problems;
    }
}
