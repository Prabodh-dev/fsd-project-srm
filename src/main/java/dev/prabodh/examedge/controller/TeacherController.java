package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.dto.ExamForm;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.service.ExamService;
import dev.prabodh.examedge.util.QueryStrings;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {

    private final ExamService examService;

    public TeacherController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AppUserDetails principal,
                            @RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("exams", examService.searchOwned(principal.getId(), q, page));
        model.addAttribute("q", q);
        model.addAttribute("extraQuery", QueryStrings.extraParameters("q", q));
        return "teacher/dashboard";
    }

    @GetMapping("/exams/new")
    public String newExam(Model model) {
        model.addAttribute("form", ExamForm.defaults());
        model.addAttribute("editing", false);
        return "teacher/exam-form";
    }

    @PostMapping("/exams")
    public String createExam(@AuthenticationPrincipal AppUserDetails principal,
                             @Valid @ModelAttribute("form") ExamForm form, BindingResult bindingResult,
                             Model model, RedirectAttributes redirectAttributes) {
        examService.windowProblems(form).forEach(problem -> bindingResult.rejectValue("endTime", "window", problem));
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            return "teacher/exam-form";
        }
        Exam exam = examService.create(form, principal.getId());
        redirectAttributes.addFlashAttribute("flashSuccess", "Exam created. Add questions next.");
        return "redirect:/teacher/exams/" + exam.getId() + "/questions";
    }

    @GetMapping("/exams/{id}/edit")
    public String editExam(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id, Model model) {
        Exam exam = examService.getOwned(id, principal.getId());
        model.addAttribute("form", ExamForm.from(exam));
        model.addAttribute("editing", true);
        model.addAttribute("examId", id);
        return "teacher/exam-form";
    }

    @PostMapping("/exams/{id}")
    public String updateExam(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                             @Valid @ModelAttribute("form") ExamForm form, BindingResult bindingResult,
                             Model model, RedirectAttributes redirectAttributes) {
        examService.windowProblems(form).forEach(problem -> bindingResult.rejectValue("endTime", "window", problem));
        if (!bindingResult.hasErrors()) {
            try {
                examService.update(id, form, principal.getId());
                redirectAttributes.addFlashAttribute("flashSuccess", "Exam updated.");
                return "redirect:/teacher";
            } catch (BusinessRuleException e) {
                bindingResult.rejectValue("durationMinutes", "locked", e.getMessage());
            }
        }
        model.addAttribute("editing", true);
        model.addAttribute("examId", id);
        return "teacher/exam-form";
    }

    @PostMapping("/exams/{id}/publish")
    public String publish(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                          RedirectAttributes redirectAttributes) {
        try {
            examService.publish(id, principal.getId());
            redirectAttributes.addFlashAttribute("flashSuccess", "Exam published.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/teacher";
    }

    @PostMapping("/exams/{id}/unpublish")
    public String unpublish(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                            RedirectAttributes redirectAttributes) {
        try {
            examService.unpublish(id, principal.getId());
            redirectAttributes.addFlashAttribute("flashSuccess", "Exam moved back to draft.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/teacher";
    }

    @PostMapping("/exams/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        examService.deleteOwned(id, principal.getId());
        redirectAttributes.addFlashAttribute("flashSuccess", "Exam deleted.");
        return "redirect:/teacher";
    }
}
