package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.SubmissionSummaryResponse;
import com.openclaw.grading.model.entity.Submission;
import com.openclaw.grading.repository.SubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SubmissionService {
    private final SubmissionRepository submissionRepository;

    public SubmissionService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public List<SubmissionSummaryResponse> listUserSubmissions(Long userId) {
        return submissionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Submission requireUserSubmission(String taskId, Long userId) {
        return submissionRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提交记录不存在"));
    }

    private SubmissionSummaryResponse toSummary(Submission submission) {
        return new SubmissionSummaryResponse(
                submission.getTaskId(),
                submission.getStatus(),
                preview(submission.getQuestion()),
                submission.getModelId(),
                submission.getModelName(),
                submission.getTotalScore(),
                submission.getMaxScore(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) return "(图片作业)";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 80 ? normalized.substring(0, 80) + "..." : normalized;
    }
}
