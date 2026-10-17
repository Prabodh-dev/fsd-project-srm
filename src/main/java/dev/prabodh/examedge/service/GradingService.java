package dev.prabodh.examedge.service;

import dev.prabodh.examedge.entity.AttemptAnswer;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class GradingService {

    public int grade(Collection<AttemptAnswer> answers) {
        int score = 0;
        for (AttemptAnswer answer : answers) {
            if (answer.isCorrect()) {
                score += answer.getQuestion().getMarks();
            }
        }
        return score;
    }

    public int awardedMarks(AttemptAnswer answer) {
        return answer.isCorrect() ? answer.getQuestion().getMarks() : 0;
    }

    public double percentage(int score, int totalMarks) {
        if (totalMarks <= 0) {
            return 0;
        }
        return Math.round(score * 1000.0 / totalMarks) / 10.0;
    }

    public boolean isPassed(int score, int totalMarks, int passingPercentage) {
        if (totalMarks <= 0) {
            return false;
        }
        return score * 100.0 / totalMarks >= passingPercentage;
    }
}
