package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.PasswordForm;
import dev.prabodh.examedge.dto.ProfileForm;
import dev.prabodh.examedge.dto.RegisterForm;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.Role;
import dev.prabodh.examedge.entity.User;
import dev.prabodh.examedge.exception.BusinessRuleException;
import dev.prabodh.examedge.exception.ResourceNotFoundException;
import dev.prabodh.examedge.repository.AttemptAnswerRepository;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.ProctoringEventRepository;
import dev.prabodh.examedge.repository.UserRepository;
import dev.prabodh.examedge.util.PageRequests;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ProctoringEventRepository proctoringEventRepository;
    private final ExamService examService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, ExamRepository examRepository,
                       AttemptRepository attemptRepository, AttemptAnswerRepository attemptAnswerRepository,
                       ProctoringEventRepository proctoringEventRepository, ExamService examService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.proctoringEventRepository = proctoringEventRepository;
        this.examService = examService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterForm form) {
        String email = form.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        Role role = Role.valueOf(form.getRole());
        if (role == Role.ADMIN) {
            throw new BusinessRuleException("This role cannot be self-registered.");
        }
        User user = createUser(form.getFullName().trim(), email, form.getPassword(), role);
        log.info("Registered new {} account with id {}", role, user.getId());
        return user;
    }

    @Transactional
    public User createUser(String fullName, String email, String rawPassword, Role role) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User get(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Transactional
    public User updateProfile(Long userId, ProfileForm form) {
        User user = get(userId);
        user.setFullName(form.getFullName().trim());
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordForm form) {
        User user = get(userId);
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user id {}", userId);
    }

    @Transactional(readOnly = true)
    public Page<User> search(String term, Role role, int page) {
        String cleaned = PageRequests.normalizeSearch(term).toLowerCase();
        Specification<User> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!cleaned.isEmpty()) {
                String pattern = "%" + cleaned + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), role));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(specification,
                PageRequests.of(page, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional
    public void setEnabled(Long adminId, Long userId, boolean enabled) {
        requireOtherUser(adminId, userId);
        User user = get(userId);
        user.setEnabled(enabled);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        log.info("Admin {} set enabled={} for user {}", adminId, enabled, userId);
    }

    @Transactional
    public void changeRole(Long adminId, Long userId, Role role) {
        requireOtherUser(adminId, userId);
        User user = get(userId);
        if (user.getRole() == Role.TEACHER && role != Role.TEACHER && examRepository.countByTeacherId(userId) > 0) {
            throw new BusinessRuleException("This teacher still owns exams. Remove those exams before changing the role.");
        }
        user.setRole(role);
        userRepository.save(user);
        log.info("Admin {} changed role of user {} to {}", adminId, userId, role);
    }

    @Transactional
    public void delete(Long adminId, Long userId) {
        requireOtherUser(adminId, userId);
        User user = get(userId);
        for (Exam exam : examRepository.findByTeacherId(userId)) {
            examService.deleteCascade(exam);
        }
        proctoringEventRepository.deleteByStudentId(userId);
        attemptAnswerRepository.deleteByStudentId(userId);
        attemptRepository.deleteByStudentId(userId);
        userRepository.delete(user);
        log.info("Admin {} deleted user {}", adminId, userId);
    }

    private void requireOtherUser(Long adminId, Long userId) {
        if (adminId.equals(userId)) {
            throw new BusinessRuleException("You cannot perform this action on your own account.");
        }
    }
}
