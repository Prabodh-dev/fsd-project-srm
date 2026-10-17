package dev.prabodh.examedge.exception;

import java.util.List;

public class InvalidQuestionException extends BusinessRuleException {

    private final List<String> problems;

    public InvalidQuestionException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
