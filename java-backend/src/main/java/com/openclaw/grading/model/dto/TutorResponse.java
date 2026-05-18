package com.openclaw.grading.model.dto;

import lombok.Data;

/**
 * 答疑对话响应
 */
@Data
public class TutorResponse {
    
    /**
     * 回答内容
     */
    private String answer;
    
    /**
     * 相关知识点
     */
    private String relatedKnowledge;
    
    /**
     * 示例（可选）
     */
    private String example;
    
    /**
     * 进一步学习建议
     */
    private String furtherStudy;
}
