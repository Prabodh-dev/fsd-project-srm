package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.QuestionForm;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.Question;
import dev.prabodh.examedge.entity.QuestionOption;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.InvalidQuestionException;
import dev.prabodh.examedge.exception.ResourceNotFoundException;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.QuestionRepository;
import dev.prabodh.examedge.service.QuestionCsvImporter.ImportResult;
import dev.prabodh.examedge.service.QuestionCsvImporter.RowError;
import dev.prabodh.examedge.util.PageRequests;
import dev.prabodh.examedge.util.QuestionDraft;
import java.io.IOException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionService {

    public static final int MAX_QUESTIONS_PER_EXAM = 200;

    public record ImportOutcome(int imported, int rowCount, List<RowError> errors) {
    }

    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final ExamService examService;
    private final QuestionCsvImporter csvImporter;

    public QuestionService(QuestionRepository questionRepository, AttemptRepository attemptRepository,
                           ExamService examService, QuestionCsvImporter csvImporter) {
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.examService = examService;
        this.csvImporter = csvImporter;
    }

    @Transactional(readOnly = true)
    public Page<Question> search(Long examId, Long teacherId, String term, int page) {
        examService.getOwned(examId, teacherId);
        return questionRepository.findByExamIdAndTextContainingIgnoreCase(examId,
                PageRequests.normalizeSearch(term),
                PageRequests.of(page, Sort.by(Sort.Direction.ASC, "displayOrder")));
    }

    @Transactional(readOnly = true)
    public Question get(Long examId, Long questionId, Long teacherId) {
        examService.getOwned(examId, teacherId);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (!question.getExam().getId().equals(examId)) {
            throw new ResourceNotFoundException("Question not found.");
        }
        return question;
    }

    @Transactional
    public Question create(Long examId, Long teacherId, QuestionForm form) {
        Exam exam = examService.getOwned(examId, teacherId);
        requireEditable(exam);
        if (questionRepository.countByExamId(examId) >= MAX_QUESTIONS_PER_EXAM) {
            throw new BusinessRuleException("An exam can contain at most " + MAX_QUESTIONS_PER_EXAM + " questions.");
        }
        QuestionDraft draft = validated(form.toDraft());
        Question saved = persistDraft(exam, draft);
        examService.refreshTotalMarks(exam);
        return saved;
    }

    @Transactional
    public Question update(Long examId, Long questionId, Long teacherId, QuestionForm form) {
        Question question = get(examId, questionId, teacherId);
        requireEditable(question.getExam());
        QuestionDraft draft = validated(form.toDraft());
        question.setText(draft.text());
        question.setMarks(draft.marks());
        question.getOptions().clear();
        questionRepository.saveAndFlush(question);
        appendOptions(question, draft);
        Question saved = questionRepository.save(question);
        examService.refreshTotalMarks(question.getExam());
        return saved;
    }

    @Transactional
    public void delete(Long examId, Long questionId, Long teacherId) {
        Question question = get(examId, questionId, teacherId);
        Exam exam = question.getExam();
        requireEditable(exam);
        questionRepository.delete(question);
        questionRepository.flush();
        renumber(examId);
        examService.refreshTotalMarks(exam);
    }

    @Transactional
    public void move(Long examId, Long questionId, Long teacherId, boolean up) {
        Question question = get(examId, questionId, teacherId);
        requireEditable(question.getExam());
        List<Question> ordered = questionRepository.findByExamIdOrderByDisplayOrderAsc(examId);
        int index = ordered.indexOf(question);
        int neighborIndex = up ? index - 1 : index + 1;
        if (index < 0 || neighborIndex < 0 || neighborIndex >= ordered.size()) {
            return;
        }
        Question neighbor = ordered.get(neighborIndex);
        int currentOrder = question.getDisplayOrder();
        question.setDisplayOrder(neighbor.getDisplayOrder());
        neighbor.setDisplayOrder(currentOrder);
        questionRepository.saveAll(List.of(question, neighbor));
    }

    @Transactional
    public ImportOutcome importCsv(Long examId, Long teacherId, MultipartFile file) {
        Exam exam = examService.getOwned(examId, teacherId);
        requireEditable(exam);
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a CSV file to import.");
        }
        ImportResult result;
        try {
            result = csvImporter.parse(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessRuleException("The file could not be read.");
        }
        long existing = questionRepository.countByExamId(examId);
        int imported = 0;
        for (QuestionDraft draft : result.validDrafts()) {
            if (existing + imported >= MAX_QUESTIONS_PER_EXAM) {
                throw new BusinessRuleException("An exam can contain at most " + MAX_QUESTIONS_PER_EXAM + " questions.");
            }
            persistDraft(exam, draft);
            imported++;
        }
        examService.refreshTotalMarks(exam);
        return new ImportOutcome(imported, result.rowCount(), result.errors());
    }

    private QuestionDraft validated(QuestionDraft draft) {
        List<String> problems = draft.problems();
        if (!problems.isEmpty()) {
            throw new InvalidQuestionException(problems);
        }
        return draft;
    }

    private Question persistDraft(Exam exam, QuestionDraft draft) {
        Question question = new Question();
        question.setExam(exam);
        question.setText(draft.text());
        question.setMarks(draft.marks());
        question.setDisplayOrder(questionRepository.findMaxDisplayOrder(exam.getId()) + 1);
        appendOptions(question, draft);
        return questionRepository.saveAndFlush(question);
    }

    private void appendOptions(Question question, QuestionDraft draft) {
        for (int i = 0; i < draft.options().size(); i++) {
            QuestionOption option = new QuestionOption();
            option.setQuestion(question);
            option.setText(draft.options().get(i));
            option.setCorrect(i == draft.correctIndex());
            question.getOptions().add(option);
        }
    }

    private void renumber(Long examId) {
        List<Question> ordered = questionRepository.findByExamIdOrderByDisplayOrderAsc(examId);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setDisplayOrder(i + 1);
        }
        questionRepository.saveAll(ordered);
    }

    private void requireEditable(Exam exam) {
        if (attemptRepository.existsByExamId(exam.getId())) {
            throw new BusinessRuleException("Questions cannot change after students have attempted this exam.");
        }
    }
}
