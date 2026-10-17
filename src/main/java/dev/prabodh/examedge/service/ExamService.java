package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.ExamForm;
import dev.prabodh.examedge.dto.Views.ExamSummary;
import dev.prabodh.examedge.dto.Views.StudentExamCard;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.ExamStatus;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.User;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.ResourceNotFoundException;
import dev.prabodh.examedge.repository.AttemptAnswerRepository;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.ProctoringEventRepository;
import dev.prabodh.examedge.repository.QuestionRepository;
import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.util.PageRequests;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ProctoringEventRepository proctoringEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ExamService(ExamRepository examRepository, QuestionRepository questionRepository,
                       AttemptRepository attemptRepository, AttemptAnswerRepository attemptAnswerRepository,
                       ProctoringEventRepository proctoringEventRepository, UserRepository userRepository,
                       Clock clock) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.proctoringEventRepository = proctoringEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ExamSummary> searchOwned(Long teacherId, String term, int page) {
        return search(teacherId, false, term, page).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ExamSummary> searchAll(String term, int page) {
        return search(null, false, term, page).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<StudentExamCard> searchForStudent(Long studentId, String term, int page) {
        return search(null, true, term, page).map(exam -> toStudentCard(exam, studentId));
    }

    private Page<Exam> search(Long teacherId, boolean publishedOnly, String term, int page) {
        String cleaned = PageRequests.normalizeSearch(term).toLowerCase();
        Specification<Exam> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (teacherId != null) {
                predicates.add(builder.equal(root.get("teacher").get("id"), teacherId));
            }
            if (publishedOnly) {
                predicates.add(builder.isTrue(root.get("published")));
            }
            if (!cleaned.isEmpty()) {
                predicates.add(builder.like(builder.lower(root.get("title")), "%" + cleaned + "%"));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return examRepository.findAll(specification,
                PageRequests.of(page, Sort.by(Sort.Direction.DESC, "startTime")));
    }

    @Transactional(readOnly = true)
    public Exam get(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
    }

    @Transactional(readOnly = true)
    public Exam getOwned(Long examId, Long teacherId) {
        Exam exam = get(examId);
        if (!exam.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("Not the owner of this exam");
        }
        return exam;
    }

    @Transactional(readOnly = true)
    public ExamSummary summaryOf(Long examId) {
        return toSummary(get(examId));
    }

    public ExamSummary toSummary(Exam exam) {
        return new ExamSummary(exam.getId(), exam.getTitle(), exam.getDescription(),
                exam.getTeacher().getFullName(), exam.getDurationMinutes(), exam.getStartTime(),
                exam.getEndTime(), exam.getTotalMarks(), exam.getPassingPercentage(), exam.isPublished(),
                exam.isShuffleQuestions(), exam.isAllowRetake(), exam.getMaxViolations(),
                exam.statusAt(LocalDateTime.now(clock)), questionRepository.countByExamId(exam.getId()),
                attemptRepository.countByExamId(exam.getId()));
    }

    public StudentExamCard toStudentCard(Exam exam, Long studentId) {
        ExamSummary summary = toSummary(exam);
        Optional<Attempt> active = attemptRepository.findByExamIdAndStudentIdAndStatus(
                exam.getId(), studentId, AttemptStatus.IN_PROGRESS);
        long completedCount = attemptRepository.countByExamIdAndStudentIdAndStatusNot(
                exam.getId(), studentId, AttemptStatus.IN_PROGRESS);
        boolean completed = completedCount > 0;
        boolean live = summary.status() == ExamStatus.LIVE;
        boolean canStart = live && (active.isPresent() || !completed || exam.isAllowRetake());
        return new StudentExamCard(summary, active.map(Attempt::getId).orElse(null), completed, canStart);
    }

    public List<String> windowProblems(ExamForm form) {
        List<String> problems = new ArrayList<>();
        if (form.getStartTime() != null && form.getEndTime() != null && form.getDurationMinutes() != null) {
            if (!form.getEndTime().isAfter(form.getStartTime())) {
                problems.add("End time must be after the start time.");
            } else if (java.time.Duration.between(form.getStartTime(), form.getEndTime()).toMinutes()
                    < form.getDurationMinutes()) {
                problems.add("The exam window must be at least as long as the duration.");
            }
        }
        return problems;
    }

    @Transactional
    public Exam create(ExamForm form, Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found."));
        Exam exam = new Exam();
        exam.setTeacher(teacher);
        applyForm(exam, form);
        Exam saved = examRepository.save(exam);
        log.info("Exam {} created by teacher {}", saved.getId(), teacherId);
        return saved;
    }

    @Transactional
    public Exam update(Long examId, ExamForm form, Long teacherId) {
        Exam exam = getOwned(examId, teacherId);
        if (attemptRepository.existsByExamId(examId)
                && exam.getDurationMinutes() != form.getDurationMinutes()) {
            throw new BusinessRuleException("The duration cannot change after students have attempted this exam.");
        }
        applyForm(exam, form);
        return examRepository.save(exam);
    }

    private void applyForm(Exam exam, ExamForm form) {
        exam.setTitle(form.getTitle().trim());
        String description = form.getDescription() == null ? "" : form.getDescription().trim();
        exam.setDescription(description.isEmpty() ? null : description);
        exam.setDurationMinutes(form.getDurationMinutes());
        exam.setStartTime(form.getStartTime());
        exam.setEndTime(form.getEndTime());
        exam.setPassingPercentage(form.getPassingPercentage());
        exam.setMaxViolations(form.getMaxViolations());
        exam.setShuffleQuestions(form.isShuffleQuestions());
        exam.setAllowRetake(form.isAllowRetake());
    }

    @Transactional
    public void publish(Long examId, Long teacherId) {
        Exam exam = getOwned(examId, teacherId);
        List<Question> questions = questionRepository.findByExamIdOrderByDisplayOrderAsc(examId);
        if (questions.isEmpty()) {
            throw new BusinessRuleException("Add at least one question before publishing.");
        }
        for (Question question : questions) {
            long correct = question.getOptions().stream().filter(option -> option.isCorrect()).count();
            if (question.getOptions().size() < 2 || correct != 1) {
                throw new BusinessRuleException("Question " + question.getDisplayOrder()
                        + " needs at least two options and exactly one correct option.");
            }
        }
        if (!exam.getEndTime().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("The end time has already passed. Edit the exam window first.");
        }
        exam.setTotalMarks(questionRepository.sumMarks(examId));
        exam.setPublished(true);
        examRepository.save(exam);
        log.info("Exam {} published", examId);
    }

    @Transactional
    public void unpublish(Long examId, Long teacherId) {
        Exam exam = getOwned(examId, teacherId);
        if (attemptRepository.existsByExamId(examId)
                && exam.statusAt(LocalDateTime.now(clock)) == ExamStatus.LIVE) {
            throw new BusinessRuleException("A live exam with attempts cannot be unpublished.");
        }
        exam.setPublished(false);
        examRepository.save(exam);
        log.info("Exam {} unpublished", examId);
    }

    @Transactional
    public void deleteOwned(Long examId, Long teacherId) {
        deleteCascade(getOwned(examId, teacherId));
    }

    @Transactional
    public void deleteAsAdmin(Long examId) {
        deleteCascade(get(examId));
    }

    @Transactional
    public void deleteCascade(Exam exam) {
        Long examId = exam.getId();
        proctoringEventRepository.deleteByExamId(examId);
        attemptAnswerRepository.deleteByExamId(examId);
        attemptRepository.deleteByExamId(examId);
        examRepository.delete(exam);
        examRepository.flush();
        log.info("Exam {} deleted", examId);
    }

    @Transactional
    public void refreshTotalMarks(Exam exam) {
        exam.setTotalMarks(questionRepository.sumMarks(exam.getId()));
        examRepository.save(exam);
    }
}
