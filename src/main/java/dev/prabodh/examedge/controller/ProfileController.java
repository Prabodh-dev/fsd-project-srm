package dev.prabodh.examedge.controller;

import dev.prabodh.examedge.config.AppUserDetails;
import dev.prabodh.examedge.dto.PasswordForm;
import dev.prabodh.examedge.dto.ProfileForm;
import dev.prabodh.examedge.entity.User;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String view(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = userService.get(principal.getId());
        ProfileForm profileForm = new ProfileForm();
        profileForm.setFullName(user.getFullName());
        model.addAttribute("profileForm", profileForm);
        model.addAttribute("passwordForm", new PasswordForm());
        model.addAttribute("account", user);
        return "profile";
    }

    @PostMapping("/name")
    public String updateName(@AuthenticationPrincipal AppUserDetails principal,
                             @Valid @ModelAttribute("profileForm") ProfileForm profileForm,
                             BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("passwordForm", new PasswordForm());
            model.addAttribute("account", userService.get(principal.getId()));
            return "profile";
        }
        User updated = userService.updateProfile(principal.getId(), profileForm);
        principal.setFullName(updated.getFullName());
        redirectAttributes.addFlashAttribute("flashSuccess", "Your name was updated.");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal AppUserDetails principal,
                                 @Valid @ModelAttribute("passwordForm") PasswordForm passwordForm,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (passwordForm.getNewPassword() != null
                && !passwordForm.getNewPassword().equals(passwordForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (!bindingResult.hasErrors()) {
            try {
                userService.changePassword(principal.getId(), passwordForm);
                redirectAttributes.addFlashAttribute("flashSuccess", "Your password was changed.");
                return "redirect:/profile";
            } catch (BusinessRuleException e) {
                bindingResult.rejectValue("currentPassword", "incorrect", e.getMessage());
            }
        }
        User user = userService.get(principal.getId());
        ProfileForm profileForm = new ProfileForm();
        profileForm.setFullName(user.getFullName());
        model.addAttribute("profileForm", profileForm);
        model.addAttribute("account", user);
        return "profile";
    }
}
