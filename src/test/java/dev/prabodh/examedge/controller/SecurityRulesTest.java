package dev.prabodh.examedge.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void publicPagesAreOpen() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        for (String path : new String[]{"/admin", "/teacher", "/student", "/profile", "/student/history"}) {
            mockMvc.perform(get(path)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    @WithUserDetails("student@examedge.com")
    void studentCannotReachTeacherOrAdminAreas() throws Exception {
        mockMvc.perform(get("/teacher")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/student")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("teacher@examedge.com")
    void teacherCannotReachStudentOrAdminAreas() throws Exception {
        mockMvc.perform(get("/student")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
        mockMvc.perform(get("/teacher")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("admin@examedge.com")
    void adminReachesAdminAreaOnly() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/users")).andExpect(status().isOk());
        mockMvc.perform(get("/teacher")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("student@examedge.com")
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/profile/name").param("fullName", "Changed")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin@examedge.com")
    void adminCannotDeleteOwnAccount() throws Exception {
        Long adminId = userRepository.findByEmailIgnoreCase("admin@examedge.com").orElseThrow().getId();
        mockMvc.perform(post("/admin/users/" + adminId + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(userRepository.existsById(adminId));
    }

    @Test
    void registrationCannotCreateAnAdmin() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Mallory Admin").param("email", "mallory@example.com")
                        .param("password", "Passw0rdOk").param("confirmPassword", "Passw0rdOk")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(view().name("register"));
        assertFalse(userRepository.existsByEmailIgnoreCase("mallory@example.com"));
    }

    @Test
    void registrationCreatesStudentAccount() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "New Student").param("email", "new.student@example.com")
                        .param("password", "Passw0rdOk").param("confirmPassword", "Passw0rdOk")
                        .param("role", "STUDENT"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        assertTrue(userRepository.existsByEmailIgnoreCase("new.student@example.com"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "").param("email", "not-an-email")
                        .param("password", "short").param("confirmPassword", "different")
                        .param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Full name is required")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter a valid email address")));
    }

    @Test
    void accountLocksAfterRepeatedFailedLogins() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login").with(csrf()).param("email", "teacher@examedge.com").param("password", "wrong"));
        }
        mockMvc.perform(post("/login").with(csrf()).param("email", "teacher@examedge.com").param("password", "Teacher@123"))
                .andExpect(redirectedUrl("/login?locked"));
    }
}
