package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.dto.Views.AttemptResultView;
import dev.prabodh.examedge.dto.Views.ExamSessionView;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.exception.AttemptClosedException;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.ResourceNotFoundException;
import dev.prabodh.examedge.service.AttemptService;
import dev.prabodh.examedge.service.AttemptService.SubmitOutcome;
import dev.prabodh.examedge.service.DashboardService;
import dev.prabodh.examedge.service.ExamService;
import dev.prabodh.examedge.service.PdfService;
import dev.prabodh.examedge.service.ResultService;
import dev.prabodh.examedge.util.QueryStrings;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final DashboardService dashboardService;
    private final ExamService examService;
    private final AttemptService attemptService;
    private final ResultService resultService;
    private final PdfService pdfService;

    public StudentController(DashboardService dashboardService, ExamService examService,
                             AttemptService attemptService, ResultService resultService, PdfService pdfService) {
        this.dashboardService = dashboardService;
        this.examService = examService;
        this.attemptService = attemptService;
        this.resultService = resultService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("dashboard", dashboardService.studentDashboard(principal.getId()));
        return "student/dashboard";
    }

    @GetMapping("/exams")
    public String exams(@AuthenticationPrincipal AppUserDetails principal,
                        @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                        Model model) {
        model.addAttribute("cards", examService.searchForStudent(principal.getId(), q, page));
        model.addAttribute("q", q);
        model.addAttribute("extraQuery", QueryStrings.extraParameters("q", q));
        return "student/exams";
    }

    @GetMapping("/exams/{examId}/instructions")
    public String instructions(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                               Model model) {
        Exam exam = examService.get(examId);
        if (!exam.isPublished()) {
            throw new ResourceNotFoundException("Exam not found.");
        }
        model.addAttribute("card", examService.toStudentCard(exam, principal.getId()));
        model.addAttribute("hasCompleted", resultService.hasCompleted(examId, principal.getId()));
        return "student/instructions";
    }

    @PostMapping("/exams/{examId}/start")
    public String start(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                        RedirectAttributes redirectAttributes) {
        try {
            Attempt attempt = attemptService.start(examId, principal.getId());
            return "redirect:/student/attempts/" + attempt.getId();
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/student/exams/" + examId + "/instructions";
        }
    }

    @GetMapping("/attempts/{attemptId}")
    public String attempt(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long attemptId,
                          Model model, RedirectAttributes redirectAttributes) {
        try {
            ExamSessionView session = attemptService.buildSession(attemptId, principal.getId());
            model.addAttribute("examSession", session);
            return "student/exam";
        } catch (AttemptClosedException e) {
            redirectAttributes.addFlashAttribute("flashSuccess", e.getMessage());
            return "redirect:/student/attempts/" + attemptId + "/result";
        }
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public String submit(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long attemptId,
                         @RequestParam(defaultValue = "false") boolean automatic,
                         RedirectAttributes redirectAttributes) {
        SubmitOutcome outcome = attemptService.submit(attemptId, principal.getId(), automatic);
        if (outcome.lateRejected()) {
            redirectAttributes.addFlashAttribute("flashError",
                    "The submission window had closed. Your saved answers were graded.");
        } else if (!outcome.alreadyClosed()) {
            redirectAttributes.addFlashAttribute("flashSuccess", "Your exam was submitted.");
        }
        return "redirect:/student/attempts/" + attemptId + "/result";
    }

    @GetMapping("/attempts/{attemptId}/result")
    public String result(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long attemptId,
                         Model model, RedirectAttributes redirectAttributes) {
        try {
            AttemptResultView result = resultService.resultForStudent(attemptId, principal.getId());
            model.addAttribute("result", result);
            return "student/result";
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/student/attempts/" + attemptId;
        }
    }

    @GetMapping("/attempts/{attemptId}/result.pdf")
    public ResponseEntity<byte[]> resultPdf(@AuthenticationPrincipal AppUserDetails principal,
                                            @PathVariable Long attemptId) {
        AttemptResultView result = resultService.resultForStudent(attemptId, principal.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("examedge-result-" + attemptId + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdfService.renderResult(result));
    }

    @GetMapping("/exams/{examId}/leaderboard")
    public String leaderboard(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                              Model model, RedirectAttributes redirectAttributes) {
        if (!resultService.hasCompleted(examId, principal.getId())) {
            redirectAttributes.addFlashAttribute("flashError", "Complete this exam to see its leaderboard.");
            return "redirect:/student/exams/" + examId + "/instructions";
        }
        model.addAttribute("exam", examService.summaryOf(examId));
        model.addAttribute("rows", resultService.leaderboard(examId, principal.getId()));
        return "student/leaderboard";
    }

    @GetMapping("/history")
    public String history(@AuthenticationPrincipal AppUserDetails principal,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("attempts", resultService.historyFor(principal.getId(), page));
        model.addAttribute("extraQuery", "");
        return "student/history";
    }
}
