package dev.prabodh.examedge.repository;

import dev.prabodh.examedge.entity.Question;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByExamIdOrderByDisplayOrderAsc(Long examId);

    Page<Question> findByExamIdAndTextContainingIgnoreCase(Long examId, String text, Pageable pageable);

    long countByExamId(Long examId);

    @Query("select coalesce(max(q.displayOrder), 0) from Question q where q.exam.id = :examId")
    int findMaxDisplayOrder(@Param("examId") Long examId);

    @Query("select coalesce(sum(q.marks), 0) from Question q where q.exam.id = :examId")
    int sumMarks(@Param("examId") Long examId);
}
