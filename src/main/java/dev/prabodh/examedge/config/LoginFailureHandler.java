package dev.prabodh.examedge.config;

import dev.prabodh.examedge.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final LoginAttemptService loginAttemptService;

    public LoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String target = "/login?error";
        if (exception instanceof LockedException) {
            target = "/login?locked";
        } else if (exception instanceof DisabledException) {
            target = "/login?disabled";
        } else if (exception instanceof BadCredentialsException) {
            boolean lockedNow = loginAttemptService.recordFailure(request.getParameter("email"));
            if (lockedNow) {
                target = "/login?locked";
            }
        }
        log.warn("Sign in rejected: {}", exception.getClass().getSimpleName());
        response.sendRedirect(request.getContextPath() + target);
    }
}
