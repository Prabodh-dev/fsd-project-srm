package dev.prabodh.examedge.repository;

import dev.prabodh.examedge.entity.ProctoringEvent;
import dev.prabodh.examedge.entity.ProctoringEventType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProctoringEventRepository extends JpaRepository<ProctoringEvent, Long> {

    List<ProctoringEvent> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);

    long countByAttemptIdAndEventTypeIn(Long attemptId, Collection<ProctoringEventType> types);

    @Modifying
    @Query("delete from ProctoringEvent e where e.attempt in (select a from Attempt a where a.exam.id = :examId)")
    void deleteByExamId(@Param("examId") Long examId);

    @Modifying
    @Query("delete from ProctoringEvent e where e.attempt in (select a from Attempt a where a.student.id = :studentId)")
    void deleteByStudentId(@Param("studentId") Long studentId);
}
