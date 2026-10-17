package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.entity.ProctoringEventType;
import dev.prabodh.examedge.service.AttemptService;
import dev.prabodh.examedge.service.AttemptService.AnswerOutcome;
import dev.prabodh.examedge.service.AttemptService.ProctoringOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student/api/attempts/{attemptId}")
@PreAuthorize("hasRole('STUDENT')")
public class ExamApiController {

    public record AnswerRequest(Long questionId, Long optionId) {
    }

    public record FlagRequest(Long questionId, boolean flagged) {
    }

    public record ProctoringRequest(String eventType) {
    }

    private final AttemptService attemptService;

    public ExamApiController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @GetMapping("/state")
    public Map<String, Object> state(@AuthenticationPrincipal AppUserDetails principal,
                                     @PathVariable Long attemptId) {
        return attemptService.stateOf(attemptId, principal.getId());
    }

    @PostMapping("/answer")
    public Map<String, Object> answer(@AuthenticationPrincipal AppUserDetails principal,
                                      @PathVariable Long attemptId, @RequestBody AnswerRequest request) {
        AnswerOutcome outcome = attemptService.saveAnswer(attemptId, principal.getId(),
                request.questionId(), request.optionId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("saved", true);
        body.put("remainingSeconds", outcome.remainingSeconds());
        return body;
    }

    @PostMapping("/flag")
    public Map<String, Object> flag(@AuthenticationPrincipal AppUserDetails principal,
                                    @PathVariable Long attemptId, @RequestBody FlagRequest request) {
        attemptService.setFlag(attemptId, principal.getId(), request.questionId(), request.flagged());
        return Map.of("saved", true);
    }

    @PostMapping("/proctor")
    public Map<String, Object> proctor(@AuthenticationPrincipal AppUserDetails principal,
                                       @PathVariable Long attemptId, @RequestBody ProctoringRequest request) {
        ProctoringEventType type;
        try {
            type = ProctoringEventType.valueOf(request.eventType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new dev.prabodh.examedge.exception.BusinessRuleException("Unknown event type.");
        }
        if (type == ProctoringEventType.VIOLATION_LIMIT_REACHED) {
            throw new dev.prabodh.examedge.exception.BusinessRuleException("Unknown event type.");
        }
        ProctoringOutcome outcome = attemptService.recordProctoringEvent(attemptId, principal.getId(), type);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("violations", outcome.violations());
        body.put("maxViolations", outcome.maxViolations());
        body.put("autoSubmitted", outcome.autoSubmitted());
        body.put("resultUrl", "/student/attempts/" + attemptId + "/result");
        return body;
    }
}
