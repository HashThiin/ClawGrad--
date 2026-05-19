package com.openclaw.grading.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 通用作业批改请求（支持全科目）
 */
@Data
public class AssignmentGradingRequest {
    
    /**
     * 题目内容
     */
    @NotBlank(message = "题目内容不能为空")
    private String question;
    
    /**
     * 学生答案（仅上传图片时可为空，校验在 UploadStage 中进行）
     */
    private String answer;
    
    /**
     * 附件URL列表（图片、文档等，保留传统字段以防外部已上传资源）
     */
    private List<String> attachments;
    
    /**
     * 满分（默认100）
     */
    private Double maxScore = 100.0;

    /**
     * 选择的模型id（为空时使用默认模型）
     */
    private String modelId;
}
