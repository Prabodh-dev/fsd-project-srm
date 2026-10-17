package dev.prabodh.examedge.service;

import dev.prabodh.examedge.dto.Views.AdminDashboard;
import dev.prabodh.examedge.dto.Views.AttemptSummary;
import dev.prabodh.examedge.dto.Views.StudentDashboard;
import dev.prabodh.examedge.dto.Views.StudentExamCard;
import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptStatus;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.entity.ExamStatus;
import dev.prabodh.examedge.entity.Role;
import dev.prabodh.examedge.repository.AttemptRepository;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int CHART_DAYS = 14;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd MMM");

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final AttemptRepository attemptRepository;
    private final ExamService examService;
    private final ResultService resultService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DashboardService(UserRepository userRepository, ExamRepository examRepository,
                            AttemptRepository attemptRepository, ExamService examService,
                            ResultService resultService, ObjectMapper objectMapper, Clock clock) {
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.attemptRepository = attemptRepository;
        this.examService = examService;
        this.resultService = resultService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AdminDashboard adminDashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(CHART_DAYS - 1L);
        LocalDateTime since = firstDay.atStartOfDay();
        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();
        for (int i = 0; i < CHART_DAYS; i++) {
            perDay.put(firstDay.plusDays(i), new long[2]);
        }
        userRepository.findCreationTimesSince(since)
                .forEach(time -> increment(perDay, time.toLocalDate(), 0));
        attemptRepository.findStartTimesSince(since)
                .forEach(time -> increment(perDay, time.toLocalDate(), 1));
        List<String> labels = new ArrayList<>();
        List<Long> registrations = new ArrayList<>();
        List<Long> attempts = new ArrayList<>();
        perDay.forEach((day, values) -> {
            labels.add(DAY_LABEL.format(day));
            registrations.add(values[0]);
            attempts.add(values[1]);
        });
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("labels", labels);
        chart.put("registrations", registrations);
        chart.put("attempts", attempts);
        long students = userRepository.count((root, query, builder) -> builder.equal(root.get("role"), Role.STUDENT));
        long teachers = userRepository.count((root, query, builder) -> builder.equal(root.get("role"), Role.TEACHER));
        return new AdminDashboard(userRepository.count(), examRepository.count(), attemptRepository.count(),
                students, teachers, toJson(chart));
    }

    private void increment(Map<LocalDate, long[]> perDay, LocalDate day, int index) {
        long[] values = perDay.get(day);
        if (values != null) {
            values[index]++;
        }
    }

    public StudentDashboard studentDashboard(Long studentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Specification<Exam> published = (root, query, builder) -> builder.isTrue(root.get("published"));
        List<StudentExamCard> live = new ArrayList<>();
        List<StudentExamCard> upcoming = new ArrayList<>();
        for (Exam exam : examRepository.findAll(published)) {
            ExamStatus status = exam.statusAt(now);
            if (status == ExamStatus.LIVE) {
                live.add(examService.toStudentCard(exam, studentId));
            } else if (status == ExamStatus.SCHEDULED) {
                upcoming.add(examService.toStudentCard(exam, studentId));
            }
        }
        List<Attempt> finished = attemptRepository
                .findByStudentIdAndStatusNotOrderBySubmittedAtAsc(studentId, AttemptStatus.IN_PROGRESS);
        List<AttemptSummary> summaries = finished.stream().map(resultService::summarize).toList();
        double average = summaries.stream().mapToDouble(AttemptSummary::percentage).average().orElse(0);
        double best = summaries.stream().mapToDouble(AttemptSummary::percentage).max().orElse(0);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (AttemptSummary summary : summaries) {
            labels.add(summary.examTitle() + " (" + DAY_LABEL.format(summary.submittedAt()) + ")");
            values.add(summary.percentage());
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("labels", labels);
        trend.put("values", values);
        List<AttemptSummary> latestFirst = new ArrayList<>(summaries);
        java.util.Collections.reverse(latestFirst);
        List<AttemptSummary> recent = latestFirst.size() > 5 ? latestFirst.subList(0, 5) : latestFirst;
        return new StudentDashboard(live, upcoming, recent, Math.round(average * 10) / 10.0, best,
                summaries.size(), toJson(trend));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Chart data could not be serialized");
        }
    }
}
