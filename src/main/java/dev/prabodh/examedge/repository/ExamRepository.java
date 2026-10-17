package dev.prabodh.examedge.repository;

import dev.prabodh.examedge.entity.Exam;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ExamRepository extends JpaRepository<Exam, Long>, JpaSpecificationExecutor<Exam> {

    List<Exam> findByTeacherId(Long teacherId);

    long countByTeacherId(Long teacherId);
}
