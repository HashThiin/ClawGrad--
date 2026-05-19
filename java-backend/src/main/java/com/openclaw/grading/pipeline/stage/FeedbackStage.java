package com.openclaw.grading.pipeline.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import com.openclaw.grading.service.GradingTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 4：反馈整理（解析 AI 返回 + 写入任务库）。
 */
@Slf4j
@Component
public class FeedbackStage implements GradingStage {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final ObjectMapper objectMapper;
    private final GradingTaskStore taskStore;

    public FeedbackStage(ObjectMapper objectMapper, GradingTaskStore taskStore) {
        this.objectMapper = objectMapper;
        this.taskStore = taskStore;
    }

    @Override
    public String name() {
        return "feedback";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void execute(GradingContext ctx) throws Exception {
        String json = extractJson(ctx.getAiRawText());
        AssignmentGradingResult result = objectMapper.readValue(json, AssignmentGradingResult.class);
        if (result.getMaxScore() == null) {
            result.setMaxScore(ctx.getRequest().getMaxScore());
        }
        ctx.setResult(result);
        taskStore.completeTask(ctx.getTaskId(), result);
        log.info("FeedbackStage ok: score={}/{}, taskId={}",
                result.getTotalScore(), result.getMaxScore(), ctx.getTaskId());
    }

    /**
     * 从 AI 文本中抽取第一段 JSON（容忍 markdown 代码块和前后说明）
     * 同时清理 JSON 中的非法字符（换行符、控制字符等）
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
            String json = m.group();
            // 清理 JSON 键名中的非法字符（空格、换行符、制表符等）
            // 例如："total Score" -> "totalScore", "total\nScore" -> "totalScore"
            json = cleanJsonKeys(json);
            return json;
        }
        throw new IllegalStateException("未在AI返回中找到JSON块: " +
                (cleaned.length() > 500 ? cleaned.substring(0, 500) + "..." : cleaned));
    }

    /**
     * 清理 JSON 键名中的空白字符（空格、换行、制表符等）
     */
    private String cleanJsonKeys(String json) {
        // 匹配 "xxx yyy" 模式的键名（中间包含空白字符）
        Pattern keyPattern = Pattern.compile("\"([^\"]*[\\s\\n\\r\\t][^\"]*)\"");
        Matcher matcher = keyPattern.matcher(json);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String originalKey = matcher.group(1);
            // 移除所有空白字符
            String cleanedKey = originalKey.replaceAll("[\\s\\n\\r\\t]+", "");
            matcher.appendReplacement(sb, "\"" + cleanedKey + "\"");
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}
