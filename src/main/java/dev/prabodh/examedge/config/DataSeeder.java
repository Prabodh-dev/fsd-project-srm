package dev.prabodh.examedge.config;

import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.QuestionOption;
import dev.prabodh.examedge.entity.Role;
import dev.prabodh.examedge.entity.User;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.QuestionRepository;
import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[][] SAMPLE_QUESTIONS = {
            {"Which keyword is used to create a subclass in Java?", "extends", "implements", "inherits", "super", "1"},
            {"What is the default value of an int field in Java?", "0", "null", "1", "undefined", "1"},
            {"Which collection does not allow duplicate elements?", "Set", "List", "ArrayList", "LinkedList", "1"},
            {"Which HTTP method is normally used to retrieve a resource?", "GET", "POST", "DELETE", "PATCH", "1"},
            {"What does SQL stand for?", "Structured Query Language", "Simple Query Logic", "Sequential Query Language", "Standard Question Language", "1"},
            {"Which HTTP status code means that a resource was not found?", "200", "301", "404", "500", "3"},
            {"Which Java keyword prevents a method from being overridden?", "static", "final", "abstract", "private", "2"},
            {"Which SQL clause is used to filter grouped results?", "WHERE", "HAVING", "ORDER BY", "LIMIT", "2"},
            {"Which of these is a stateless protocol?", "HTTP", "FTP session", "SSH tunnel", "Telnet", "1"},
            {"What is the time complexity of binary search on a sorted array?", "O(n)", "O(n log n)", "O(log n)", "O(1)", "3"}
    };

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserService userService;

    public DataSeeder(UserRepository userRepository, ExamRepository examRepository,
                      QuestionRepository questionRepository, UserService userService) {
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            userService.createUser("System Admin", "admin@examedge.com", "Admin@123", Role.ADMIN);
            userService.createUser("Sample Teacher", "teacher@examedge.com", "Teacher@123", Role.TEACHER);
            userService.createUser("Sample Student", "student@examedge.com", "Student@123", Role.STUDENT);
            log.info("Seeded default accounts");
        }
        if (examRepository.count() == 0) {
            User teacher = userRepository.findByEmailIgnoreCase("teacher@examedge.com").orElse(null);
            if (teacher != null) {
                seedSampleExam(teacher);
            }
        }
    }

    private void seedSampleExam(User teacher) {
        Exam exam = new Exam();
        exam.setTeacher(teacher);
        exam.setTitle("Computer Science Fundamentals");
        exam.setDescription("Ten multiple choice questions covering Java, databases and web basics.");
        exam.setDurationMinutes(15);
        exam.setStartTime(LocalDateTime.now().minusDays(1));
        exam.setEndTime(LocalDateTime.now().plusYears(1));
        exam.setPassingPercentage(40);
        exam.setPublished(true);
        exam.setShuffleQuestions(true);
        exam.setAllowRetake(true);
        exam.setMaxViolations(3);
        exam.setTotalMarks(SAMPLE_QUESTIONS.length * 2);
        Exam savedExam = examRepository.save(exam);

        int order = 1;
        for (String[] row : SAMPLE_QUESTIONS) {
            Question question = new Question();
            question.setExam(savedExam);
            question.setText(row[0]);
            question.setMarks(2);
            question.setDisplayOrder(order++);
            int correctNumber = Integer.parseInt(row[5]);
            for (int i = 1; i <= 4; i++) {
                QuestionOption option = new QuestionOption();
                option.setQuestion(question);
                option.setText(row[i]);
                option.setCorrect(i == correctNumber);
                question.getOptions().add(option);
            }
            questionRepository.save(question);
        }
        log.info("Seeded sample exam with {} questions", List.of(SAMPLE_QUESTIONS).size());
    }
}
