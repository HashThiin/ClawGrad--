package com.openclaw.grading.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SubmissionSummaryResponse {
    private String taskId;
    private String status;
    private String questionPreview;
    private String modelId;
    private String modelName;
    private Double totalScore;
    private Double maxScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
