package com.openclaw.grading.repository;

import com.openclaw.grading.model.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByTaskId(String taskId);

    Optional<Submission> findByTaskIdAndUserId(String taskId, Long userId);

    List<Submission> findByUserIdOrderByCreatedAtDesc(Long userId);
}
