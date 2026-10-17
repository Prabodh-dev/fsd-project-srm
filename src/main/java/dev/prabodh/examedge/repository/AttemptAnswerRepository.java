package dev.prabodh.examedge.repository;

import dev.prabodh.examedge.entity.AttemptAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    @Query("select aa from AttemptAnswer aa left join fetch aa.selectedOption where aa.attempt.id = :attemptId")
    List<AttemptAnswer> findByAttemptIdWithSelection(@Param("attemptId") Long attemptId);

    Optional<AttemptAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    @Query("select aa.question.id, count(aa), sum(case when o.correct = true then 1 else 0 end) "
            + "from AttemptAnswer aa left join aa.selectedOption o "
            + "where aa.attempt.exam.id = :examId "
            + "and aa.attempt.status <> dev.prabodh.examedge.entity.AttemptStatus.IN_PROGRESS "
            + "group by aa.question.id")
    List<Object[]> summarizeAccuracyByQuestion(@Param("examId") Long examId);

    @Modifying
    @Query("delete from AttemptAnswer aa where aa.attempt in (select a from Attempt a where a.exam.id = :examId)")
    void deleteByExamId(@Param("examId") Long examId);

    @Modifying
    @Query("delete from AttemptAnswer aa where aa.attempt in (select a from Attempt a where a.student.id = :studentId)")
    void deleteByStudentId(@Param("studentId") Long studentId);

    @Modifying
    @Query("delete from AttemptAnswer aa where aa.question.id = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);
}
