package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.dto.QuestionForm;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.InvalidQuestionException;
import dev.prabodh.examedge.service.ExamService;
import dev.prabodh.examedge.service.QuestionService;
import dev.prabodh.examedge.service.QuestionService.ImportOutcome;
import dev.prabodh.examedge.util.QueryStrings;
import dev.prabodh.examedge.util.QuestionDraft;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teacher/exams/{examId}/questions")
@PreAuthorize("hasRole('TEACHER')")
public class QuestionController {

    private final QuestionService questionService;
    private final ExamService examService;

    public QuestionController(QuestionService questionService, ExamService examService) {
        this.questionService = questionService;
        this.examService = examService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                       @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("exam", examService.toSummary(examService.getOwned(examId, principal.getId())));
        model.addAttribute("questions", questionService.search(examId, principal.getId(), q, page));
        model.addAttribute("q", q);
        model.addAttribute("extraQuery", QueryStrings.extraParameters("q", q));
        return "teacher/questions";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId, Model model) {
        fillFormModel(model, principal, examId, new QuestionForm(), null);
        return "teacher/question-form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                         @ModelAttribute("form") QuestionForm form, Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            questionService.create(examId, principal.getId(), form);
            redirectAttributes.addFlashAttribute("flashSuccess", "Question added.");
            return "redirect:/teacher/exams/" + examId + "/questions";
        } catch (InvalidQuestionException e) {
            fillFormModel(model, principal, examId, form, null);
            model.addAttribute("problems", e.getProblems());
            return "teacher/question-form";
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/teacher/exams/" + examId + "/questions";
        }
    }

    @GetMapping("/{questionId}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                           @PathVariable Long questionId, Model model) {
        QuestionForm form = QuestionForm.from(questionService.get(examId, questionId, principal.getId()));
        fillFormModel(model, principal, examId, form, questionId);
        return "teacher/question-form";
    }

    @PostMapping("/{questionId}")
    public String update(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                         @PathVariable Long questionId, @ModelAttribute("form") QuestionForm form, Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            questionService.update(examId, questionId, principal.getId(), form);
            redirectAttributes.addFlashAttribute("flashSuccess", "Question updated.");
            return "redirect:/teacher/exams/" + examId + "/questions";
        } catch (InvalidQuestionException e) {
            fillFormModel(model, principal, examId, form, questionId);
            model.addAttribute("problems", e.getProblems());
            return "teacher/question-form";
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/teacher/exams/" + examId + "/questions";
        }
    }

    @PostMapping("/{questionId}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                         @PathVariable Long questionId, RedirectAttributes redirectAttributes) {
        try {
            questionService.delete(examId, questionId, principal.getId());
            redirectAttributes.addFlashAttribute("flashSuccess", "Question deleted.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/teacher/exams/" + examId + "/questions";
    }

    @PostMapping("/{questionId}/move")
    public String move(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                       @PathVariable Long questionId, @RequestParam boolean up,
                       @RequestParam(defaultValue = "0") int page, RedirectAttributes redirectAttributes) {
        try {
            questionService.move(examId, questionId, principal.getId(), up);
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/teacher/exams/" + examId + "/questions?page=" + Math.max(0, page);
    }

    @PostMapping("/import")
    public String importCsv(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                            @RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            ImportOutcome outcome = questionService.importCsv(examId, principal.getId(), file);
            redirectAttributes.addFlashAttribute("flashSuccess", outcome.imported() + " of "
                    + outcome.rowCount() + " rows imported.");
            if (!outcome.errors().isEmpty()) {
                redirectAttributes.addFlashAttribute("importErrors", outcome.errors());
            }
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/teacher/exams/" + examId + "/questions";
    }

    private void fillFormModel(Model model, AppUserDetails principal, Long examId, QuestionForm form,
                               Long questionId) {
        model.addAttribute("exam", examService.toSummary(examService.getOwned(examId, principal.getId())));
        model.addAttribute("form", form);
        model.addAttribute("questionId", questionId);
        model.addAttribute("optionSlots", java.util.stream.IntStream.range(0, QuestionDraft.MAX_OPTIONS).boxed().toList());
    }
}
