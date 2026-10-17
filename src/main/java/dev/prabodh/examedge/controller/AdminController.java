package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.entity.Role;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.service.DashboardService;
import dev.prabodh.examedge.service.ExamService;
import dev.prabodh.examedge.service.UserService;
import dev.prabodh.examedge.util.QueryStrings;
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
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final DashboardService dashboardService;
    private final UserService userService;
    private final ExamService examService;

    public AdminController(DashboardService dashboardService, UserService userService, ExamService examService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
        this.examService = examService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.adminDashboard());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "") String q,
                        @RequestParam(required = false) String role,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        Role roleFilter = parseRole(role);
        model.addAttribute("users", userService.search(q, roleFilter, page));
        model.addAttribute("q", q);
        model.addAttribute("role", roleFilter == null ? "" : roleFilter.name());
        model.addAttribute("roles", Role.values());
        model.addAttribute("extraQuery", QueryStrings.extraParameters("q", q,
                "role", roleFilter == null ? null : roleFilter.name()));
        return "admin/users";
    }

    @PostMapping("/users/{id}/enabled")
    public String setEnabled(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                             @RequestParam boolean enabled, @RequestParam(defaultValue = "") String returnQuery,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.setEnabled(principal.getId(), id, enabled);
            redirectAttributes.addFlashAttribute("flashSuccess", enabled ? "Account enabled." : "Account disabled.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users?" + sanitizeQuery(returnQuery);
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                             @RequestParam Role role, @RequestParam(defaultValue = "") String returnQuery,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.changeRole(principal.getId(), id, role);
            redirectAttributes.addFlashAttribute("flashSuccess", "Role updated.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users?" + sanitizeQuery(returnQuery);
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                             @RequestParam(defaultValue = "") String returnQuery,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.delete(principal.getId(), id);
            redirectAttributes.addFlashAttribute("flashSuccess", "Account deleted.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users?" + sanitizeQuery(returnQuery);
    }

    @GetMapping("/exams")
    public String exams(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                        Model model) {
        model.addAttribute("exams", examService.searchAll(q, page));
        model.addAttribute("q", q);
        model.addAttribute("extraQuery", QueryStrings.extraParameters("q", q));
        return "admin/exams";
    }

    @PostMapping("/exams/{id}/delete")
    public String deleteExam(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        examService.deleteAsAdmin(id);
        redirectAttributes.addFlashAttribute("flashSuccess", "Exam removed.");
        return "redirect:/admin/exams";
    }

    private Role parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String sanitizeQuery(String raw) {
        if (!raw.matches("[A-Za-z0-9=&%+_.\\-]*")) {
            return "";
        }
        return raw.startsWith("&") ? raw.substring(1) : raw;
    }
}
