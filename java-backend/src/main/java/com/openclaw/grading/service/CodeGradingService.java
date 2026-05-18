package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.CodeGradingRequest;
import com.openclaw.grading.model.dto.CodeGradingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 编程作业批改服务
 * 负责构建 Prompt 并调用 AI 进行批改
 */
@Slf4j
@Service
public class CodeGradingService {
    
    private final OpenClawClientService openClawClient;
    
    public CodeGradingService(OpenClawClientService openClawClient) {
        this.openClawClient = openClawClient;
    }
    
    /**
     * 批改编程作业
     * 
     * @param request 批改请求
     * @return 批改结果
     */
    public CodeGradingResult gradeCode(CodeGradingRequest request) {
        log.info("Starting code grading: language={}, requirement length={}", 
                request.getLanguage(), request.getRequirement().length());
        
        // 1. 构建 Prompt
        String prompt = buildCodeGradingPrompt(request);
        log.debug("Generated prompt:\n{}", prompt);
        
        // 2. 调用 AI 进行批改（这里简化处理，实际应该通过 OpenClaw Gateway 调用 AI）
        // TODO: 实际应该调用 openClawClient.sendMessage() 并解析 AI 返回的结果
        CodeGradingResult result = mockGradingResult(request);
        
        log.info("Code grading completed: score={}", result.getTotalScore());
        return result;
    }
    
    /**
     * 构建编程作业批改 Prompt
     */
    private String buildCodeGradingPrompt(CodeGradingRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一位专业的编程作业批改老师。请批改以下作业：\n\n");
        
        prompt.append("【语言】").append(request.getLanguage()).append("\n");
        prompt.append("【题目要求】").append(request.getRequirement()).append("\n\n");
        
        prompt.append("【学生代码】\n");
        prompt.append(request.getStudentCode()).append("\n\n");
        
        if (request.getTestCases() != null && !request.getTestCases().isEmpty()) {
            prompt.append("【测试用例】\n");
            for (int i = 0; i < request.getTestCases().size(); i++) {
                CodeGradingRequest.TestCase tc = request.getTestCases().get(i);
                prompt.append(i + 1).append(". 输入：").append(tc.getInput())
                      .append(" → 期望：").append(tc.getExpectedOutput());
                if (tc.getDescription() != null) {
                    prompt.append(" (").append(tc.getDescription()).append(")");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        if (request.getRubric() != null && !request.getRubric().isEmpty()) {
            prompt.append("【评分标准】\n");
            prompt.append(request.getRubric()).append("\n\n");
        } else {
            prompt.append("【评分标准】\n");
            prompt.append("- 正确性 40%\n");
            prompt.append("- 代码规范 30%\n");
            prompt.append("- 算法效率 30%\n\n");
        }
        
        prompt.append("请输出：\n");
        prompt.append("1. 总分（0-100）\n");
        prompt.append("2. 每个测试用例通过情况\n");
        prompt.append("3. 代码问题列表\n");
        prompt.append("4. 改进建议\n");
        prompt.append("5. 详细评语\n");
        
        return prompt.toString();
    }
    
    /**
     * Mock 批改结果（实际应该从 AI 获取）
     */
    private CodeGradingResult mockGradingResult(CodeGradingRequest request) {
        CodeGradingResult result = new CodeGradingResult();
        
        // Mock 分数
        result.setTotalScore(85.0);
        
        Map<String, Double> dimensions = new HashMap<>();
        dimensions.put("correctness", 36.0);  // 40% * 90%
        dimensions.put("readability", 25.5);  // 30% * 85%
        dimensions.put("efficiency", 23.5);   // 30% * 78%
        result.setDimensionScores(dimensions);
        
        // Mock 测试用例结果
        List<CodeGradingResult.TestCaseResult> testResults = new ArrayList<>();
        if (request.getTestCases() != null) {
            for (int i = 0; i < request.getTestCases().size(); i++) {
                CodeGradingRequest.TestCase tc = request.getTestCases().get(i);
                CodeGradingResult.TestCaseResult testResult = new CodeGradingResult.TestCaseResult();
                testResult.setTestCaseIndex(i);
                testResult.setPassed(i % 2 == 0); // Mock: 偶数通过
                testResult.setExpectedOutput(tc.getExpectedOutput());
                testResult.setActualOutput(tc.getExpectedOutput());
                testResult.setDescription(tc.getDescription());
                testResults.add(testResult);
            }
        }
        result.setTestCaseResults(testResults);
        
        // Mock 问题列表
        List<String> issues = new ArrayList<>();
        issues.add("缺少必要的注释说明");
        issues.add("变量命名不够清晰");
        result.setCodeIssues(issues);
        
        // Mock 建议
        List<String> suggestions = new ArrayList<>();
        suggestions.add("添加关键步骤的注释，提高代码可读性");
        suggestions.add("使用更具描述性的变量名");
        suggestions.add("考虑边界情况的处理");
        result.setSuggestions(suggestions);
        
        result.setFeedback("代码整体逻辑正确，实现了基本的快速排序算法。建议在代码规范和注释方面进行改进。");
        
        return result;
    }
}
