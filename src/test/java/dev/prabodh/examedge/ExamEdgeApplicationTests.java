package dev.prabodh.examedge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.QuestionRepository;
import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ExamEdgeApplicationTests {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void seedDataIsPresent() {
        assertTrue(userRepository.existsByEmailIgnoreCase("admin@examedge.com"));
        assertTrue(userRepository.existsByEmailIgnoreCase("teacher@examedge.com"));
        assertTrue(userRepository.existsByEmailIgnoreCase("student@examedge.com"));
        Long examId = examRepository.findAll().get(0).getId();
        assertEquals(10, questionRepository.countByExamId(examId));
    }
}
