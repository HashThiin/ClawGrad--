package com.openclaw.grading.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 编程作业批改请求
 */
@Data
public class CodeGradingRequest {
    
    /**
     * 编程语言：java, python, cpp, js...
     */
    @NotBlank(message = "编程语言不能为空")
    private String language;
    
    /**
     * 题目要求
     */
    @NotBlank(message = "题目要求不能为空")
    private String requirement;
    
    /**
     * 学生代码
     */
    @NotBlank(message = "学生代码不能为空")
    private String studentCode;
    
    /**
     * 测试用例
     */
    private List<TestCase> testCases;
    
    /**
     * 评分标准（可选）
     */
    private String rubric;
    
    @Data
    public static class TestCase {
        /**
         * 输入
         */
        private String input;
        
        /**
         * 期望输出
         */
        private String expectedOutput;
        
        /**
         * 描述
         */
        private String description;
    }
}
