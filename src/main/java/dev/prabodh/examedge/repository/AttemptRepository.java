package dev.prabodh.examedge.repository;

import dev.prabodh.examedge.entity.Attempt;
import dev.prabodh.examedge.entity.AttemptStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    Optional<Attempt> findByExamIdAndStudentIdAndStatus(Long examId, Long studentId, AttemptStatus status);

    long countByExamIdAndStudentIdAndStatusNot(Long examId, Long studentId, AttemptStatus status);

    boolean existsByExamId(Long examId);

    long countByExamId(Long examId);

    List<Attempt> findByStatus(AttemptStatus status);

    Page<Attempt> findByStudentIdAndStatusNot(Long studentId, AttemptStatus status, Pageable pageable);

    List<Attempt> findByStudentIdAndStatusNotOrderBySubmittedAtAsc(Long studentId, AttemptStatus status);

    List<Attempt> findByExamIdAndStatusNot(Long examId, AttemptStatus status);

    @Query(value = "select a from Attempt a join a.student s where a.exam.id = :examId "
            + "and a.status <> dev.prabodh.examedge.entity.AttemptStatus.IN_PROGRESS "
            + "and (lower(s.fullName) like :term or lower(s.email) like :term)",
            countQuery = "select count(a) from Attempt a join a.student s where a.exam.id = :examId "
                    + "and a.status <> dev.prabodh.examedge.entity.AttemptStatus.IN_PROGRESS "
                    + "and (lower(s.fullName) like :term or lower(s.email) like :term)")
    Page<Attempt> searchCompletedByExam(@Param("examId") Long examId, @Param("term") String term, Pageable pageable);

    @Query("select a.startedAt from Attempt a where a.startedAt >= :since")
    List<LocalDateTime> findStartTimesSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from Attempt a where a.exam.id = :examId")
    void deleteByExamId(@Param("examId") Long examId);

    @Modifying
    @Query("delete from Attempt a where a.student.id = :studentId")
    void deleteByStudentId(@Param("studentId") Long studentId);
}
