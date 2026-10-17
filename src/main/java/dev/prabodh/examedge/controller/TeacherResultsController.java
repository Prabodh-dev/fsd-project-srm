package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.dto.Views.AttemptResultView;
import dev.prabodh.examedge.dto.Views.ExamAnalytics;
import dev.prabodh.examedge.service.AnalyticsService;
import dev.prabodh.examedge.service.ExamService;
import dev.prabodh.examedge.service.ResultService;
import dev.prabodh.examedge.util.QueryStrings;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherResultsController {

    private final ResultService resultService;
    private final AnalyticsService analyticsService;
    private final ExamService examService;

    public TeacherResultsController(ResultService resultService, AnalyticsService analyticsService,
                                    ExamService examService) {
        this.resultService = resultService;
        this.analyticsService = analyticsService;
        this.examService = examService;
    }

    @GetMapping("/exams/{examId}/results")
    public String results(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                          @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                          Model model) {
        model.addAttribute("exam", examService.toSummary(examService.getOwned(examId, principal.getId())));
        model.addAttribute("attempts", resultService.searchExamResults(examId, principal.getId(), q, page));
        model.addAttribute("q", q);
        model.addAttribute("extraQuery", QueryStrings.extraParameters("q", q));
        return "teacher/results";
    }

    @GetMapping("/exams/{examId}/results.csv")
    public ResponseEntity<byte[]> exportResults(@AuthenticationPrincipal AppUserDetails principal,
                                                @PathVariable Long examId) {
        String csv = resultService.exportResultsCsv(examId, principal.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("exam-" + examId + "-results.csv").build());
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/exams/{examId}/analytics")
    public String analytics(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long examId,
                            Model model) throws com.fasterxml.jackson.core.JsonProcessingException {
        ExamAnalytics analytics = analyticsService.analyze(examId, principal.getId());
        model.addAttribute("exam", examService.toSummary(examService.getOwned(examId, principal.getId())));
        model.addAttribute("analytics", analytics);
        java.util.Map<String, Object> chart = new java.util.LinkedHashMap<>();
        chart.put("histogramLabels", analytics.histogramLabels());
        chart.put("histogramCounts", analytics.histogramCounts());
        chart.put("questionLabels", analytics.questionAccuracy().stream().map(a -> "Q" + a.number()).toList());
        chart.put("questionAccuracy", analytics.questionAccuracy().stream().map(a -> a.accuracyPercentage()).toList());
        model.addAttribute("chartJson", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(chart));
        return "teacher/analytics";
    }

    @GetMapping("/attempts/{attemptId}")
    public String attemptDetail(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long attemptId,
                                Model model) {
        AttemptResultView result = resultService.resultForTeacher(attemptId, principal.getId());
        model.addAttribute("result", result);
        model.addAttribute("events", resultService.eventsForTeacher(attemptId, principal.getId()));
        return "teacher/attempt-detail";
    }
}
