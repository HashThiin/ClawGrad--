package com.openclaw.grading.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 编程作业批改结果
 */
@Data
public class CodeGradingResult {
    
    /**
     * 总分（0-100）
     */
    private double totalScore;
    
    /**
     * 各维度分数
     */
    private Map<String, Double> dimensionScores;
    
    /**
     * 测试用例通过情况
     */
    private List<TestCaseResult> testCaseResults;
    
    /**
     * 代码问题列表
     */
    private List<String> codeIssues;
    
    /**
     * 改进建议
     */
    private List<String> suggestions;
    
    /**
     * 详细评语
     */
    private String feedback;
    
    /**
     * 推理步骤
     */
    private List<ReasoningStep> reasoningSteps;
    
    @Data
    public static class TestCaseResult {
        private int testCaseIndex;
        private boolean passed;
        private String actualOutput;
        private String expectedOutput;
        private String description;
    }
    
    @Data
    public static class ReasoningStep {
        private int stepIndex;
        private String thought;
        private String action;
        private Map<String, Object> actionInput;
        private String observation;
        private Map<String, Object> evidence;
    }
}
