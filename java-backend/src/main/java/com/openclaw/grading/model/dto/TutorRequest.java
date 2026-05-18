package com.openclaw.grading.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 答疑对话请求
 */
@Data
public class TutorRequest {
    
    /**
     * 科目：math, physics, programming...
     */
    @NotBlank(message = "科目不能为空")
    private String subject;
    
    /**
     * 学生问题
     */
    @NotBlank(message = "问题不能为空")
    private String question;
    
    /**
     * 相关背景（可选）
     */
    private String context;
    
    /**
     * 学生水平：elementary, highschool, university
     */
    private String studentLevel;
}
