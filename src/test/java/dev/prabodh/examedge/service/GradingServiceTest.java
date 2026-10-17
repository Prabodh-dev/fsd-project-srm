package dev.prabodh.examedge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.prabodh.examedge.entity.AttemptAnswer;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.QuestionOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradingServiceTest {

    private final GradingService gradingService = new GradingService();

    private AttemptAnswer answer(int marks, boolean selectCorrect, boolean answered) {
        Question question = new Question();
        question.setMarks(marks);
        QuestionOption correct = new QuestionOption();
        correct.setCorrect(true);
        QuestionOption wrong = new QuestionOption();
        wrong.setCorrect(false);
        question.getOptions().addAll(List.of(correct, wrong));
        AttemptAnswer answer = new AttemptAnswer();
        answer.setQuestion(question);
        if (answered) {
            answer.setSelectedOption(selectCorrect ? correct : wrong);
        }
        return answer;
    }

    @Test
    void sumsMarksOfCorrectAnswersOnly() {
        List<AttemptAnswer> answers = List.of(answer(2, true, true), answer(3, false, true), answer(5, true, true));
        assertEquals(7, gradingService.grade(answers));
    }

    @Test
    void unansweredQuestionsScoreZero() {
        assertEquals(0, gradingService.grade(List.of(answer(4, true, false))));
    }

    @Test
    void emptyAnswerSetScoresZero() {
        assertEquals(0, gradingService.grade(List.of()));
    }

    @Test
    void awardedMarksFollowCorrectness() {
        assertEquals(4, gradingService.awardedMarks(answer(4, true, true)));
        assertEquals(0, gradingService.awardedMarks(answer(4, false, true)));
    }

    @Test
    void percentageIsRoundedToOneDecimal() {
        assertEquals(66.7, gradingService.percentage(2, 3));
        assertEquals(0.0, gradingService.percentage(5, 0));
    }

    @Test
    void passDecisionUsesPassingPercentage() {
        assertTrue(gradingService.isPassed(4, 10, 40));
        assertFalse(gradingService.isPassed(3, 10, 40));
        assertFalse(gradingService.isPassed(0, 0, 0));
    }
}
