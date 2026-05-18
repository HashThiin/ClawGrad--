package com.openclaw.grading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.grading.config.OpenClawGatewayProperties;
import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.model.dto.AssignmentGradingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用作业批改服务（支持全科目）
 */
@Service
@Slf4j
public class AssignmentGradingService {

    private final OpenClawClientService openClawClientService;
    private final GradingTaskStore taskStore;
    private final OpenClawGatewayProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${openclaw.gateway.model:dashscope/glm-5}")
    private String defaultModel;

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    public AssignmentGradingService(
            OpenClawClientService openClawClientService,
            GradingTaskStore taskStore,
            OpenClawGatewayProperties properties,
            ObjectMapper objectMapper) {
        this.openClawClientService = openClawClientService;
        this.taskStore = taskStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交批改任务（异步）：立即返回 taskId，后台真实调用 OpenClaw
     */
    public String submitGradingTask(AssignmentGradingRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("Submit grading task: taskId={}, question={}", taskId, request.getQuestion());

        taskStore.createTask(taskId, request.getQuestion(), request.getAnswer());

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request);

        // 异步调用 OpenClaw chat completions
        openClawClientService.chatCompletion(defaultModel, systemPrompt, userPrompt)
                .subscribe(
                        aiText -> handleAiResponse(taskId, aiText, request.getMaxScore()),
                        error -> {
                            log.error("OpenClaw call failed: taskId={}", taskId, error);
                            taskStore.failTask(taskId, "OpenClaw error: " + error.getMessage());
                        }
                );

        return taskId;
    }

    private String buildSystemPrompt() {
        return "你是一位资深教育专家AI批改助手，能批改任何科目（数学/语文/英语/物理/化学/编程/历史等）的作业。" +
                "你必须严格按照用户指定的JSON格式输出，不要添加任何markdown代码块标记，不要添加多余文字，只输出纯JSON。";
    }

    private String buildUserPrompt(AssignmentGradingRequest request) {
        StringBuilder p = new StringBuilder();
        p.append("【题目】\n").append(request.getQuestion()).append("\n\n");
        p.append("【学生答案】\n").append(request.getAnswer()).append("\n\n");
        p.append("【满分】").append(request.getMaxScore()).append(" 分\n\n");
        p.append("【批改要求】\n")
                .append("1. 自动识别题目科目并按该科目特点评分\n")
                .append("2. 找出具体错误并给出纠正方法\n")
                .append("3. 分析知识点掌握情况\n")
                .append("4. 给出可操作的改进建议\n")
                .append("5. dimensionScores 的每个维度满分必须等于题目满分（").append(request.getMaxScore()).append("分），不要使用百分制\n")
                .append("6. 维度数量根据题目复杂度灵活：简单题1-2个维度即可，复杂题2-4个维度，不要硬凑\n\n");
        p.append("【输出格式 - 严格JSON，不要任何其他内容】\n");
        p.append("{\n")
                .append("  \"totalScore\": 数字,\n")
                .append("  \"maxScore\": ").append(request.getMaxScore()).append(",\n")
                .append("  \"dimensionScores\": {\"维度名\": 数字},\n")
                .append("  \"feedback\": \"总体评语，200字以内\",\n")
                .append("  \"errors\": [{\"location\":\"位置\",\"errorType\":\"错误类型\",\"description\":\"描述\",\"correction\":\"纠正方法\"}],\n")
                .append("  \"suggestions\": [\"建议1\",\"建议2\"],\n")
                .append("  \"knowledgePoints\": [{\"name\":\"知识点\",\"masteryLevel\":\"mastered/partial/weak\",\"description\":\"说明\"}],\n")
                .append("  \"reasoningSteps\": [\"分析步骤1\",\"分析步骤2\"]\n")
                .append("}");
        return p.toString();
    }

    /**
     * 解析 AI 返回，写入任务状态
     */
    private void handleAiResponse(String taskId, String aiText, Double maxScore) {
        log.info("AI raw response: taskId={}, length={}", taskId, aiText.length());
        try {
            String json = extractJson(aiText);
            AssignmentGradingResult result = objectMapper.readValue(json, AssignmentGradingResult.class);
            if (result.getMaxScore() == null) {
                result.setMaxScore(maxScore);
            }
            taskStore.completeTask(taskId, result);
            log.info("Grading completed: taskId={}, score={}/{}", taskId,
                    result.getTotalScore(), result.getMaxScore());
        } catch (Exception e) {
            log.error("Parse AI response failed: taskId={}, raw={}", taskId, aiText, e);
            taskStore.failTask(taskId, "解析AI返回失败: " + e.getMessage() + " | 原文: " +
                    (aiText.length() > 500 ? aiText.substring(0, 500) + "..." : aiText));
        }
    }

    /**
     * 从 AI 文本中抽取第一段 JSON（容忍包裹的 markdown 或前后说明）
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalStateException("AI 返回为空");
        }
        String cleaned = text.replaceAll("(?s)```json", "")
                .replaceAll("(?s)```", "")
                .trim();
        Matcher m = JSON_BLOCK.matcher(cleaned);
        if (m.find()) {
            return m.group();
        }
        throw new IllegalStateException("未在AI返回中找到JSON块: " + cleaned);
    }
}
