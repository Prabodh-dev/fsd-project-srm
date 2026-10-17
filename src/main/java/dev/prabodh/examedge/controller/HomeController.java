package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.dto.RegisterForm;
import dev.prabodh.examedge.exception.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import dev.prabodh.examedge.service.UserService;

@Controller
public class HomeController {

    private final UserService userService;

    public HomeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String landing() {
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails principal) {
        return switch (principal.getRole()) {
            case ADMIN -> "redirect:/admin";
            case TEACHER -> "redirect:/teacher";
            case STUDENT -> "redirect:/student";
        };
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AppUserDetails principal) {
        return principal != null ? "redirect:/dashboard" : "login";
    }

    @GetMapping("/register")
    public String registerForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @org.springframework.web.bind.annotation.ModelAttribute("form") RegisterForm form,
                           BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form);
        } catch (BusinessRuleException e) {
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            return "register";
        }
        redirectAttributes.addFlashAttribute("flashSuccess", "Your account is ready. Sign in to continue.");
        return "redirect:/login";
    }
}
