package dev.prabodh.examedge.service;

import dev.prabodh.examedge.entity.User;
import dev.prabodh.examedge.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final Clock clock;
    private final int maxFailedLogins;
    private final long lockoutMinutes;

    public LoginAttemptService(UserRepository userRepository, Clock clock,
                               @Value("${examedge.security.max-failed-logins:5}") int maxFailedLogins,
                               @Value("${examedge.security.lockout-minutes:10}") long lockoutMinutes) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxFailedLogins = maxFailedLogins;
        this.lockoutMinutes = lockoutMinutes;
    }

    @Transactional
    public boolean recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            return false;
        }
        int failures = user.getFailedLoginAttempts() + 1;
        if (failures >= maxFailedLogins) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(LocalDateTime.now(clock).plusMinutes(lockoutMinutes));
            userRepository.save(user);
            log.warn("Account with id {} locked after repeated failed sign ins", user.getId());
            return true;
        }
        user.setFailedLoginAttempts(failures);
        userRepository.save(user);
        return false;
    }

    @Transactional
    public void recordSuccess(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }
}
