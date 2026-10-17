package dev.prabodh.examedge.config;

import dev.prabodh.examedge.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final LoginAttemptService loginAttemptService;

    public LoginSuccessHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
        setDefaultTargetUrl("/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        loginAttemptService.recordSuccess(authentication.getName());
        log.info("User signed in with id {}", ((AppUserDetails) authentication.getPrincipal()).getId());
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, "/dashboard");
    }
}
