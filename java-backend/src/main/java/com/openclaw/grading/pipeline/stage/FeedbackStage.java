package com.openclaw.grading.pipeline.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.model.dto.HomeworkItem;
import com.openclaw.grading.model.dto.ItemGradingResult;
import com.openclaw.grading.model.dto.OrganizedHomework;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import com.openclaw.grading.service.GradingTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        OrganizedHomework hw = ctx.getOrganizedHomework();
        Double totalMax = hw != null && hw.getTotalMaxScore() != null
                ? hw.getTotalMaxScore()
                : ctx.getRequest().getMaxScore();
        if (result.getMaxScore() == null) {
            result.setMaxScore(totalMax);
        }

        // 回填逐题题目原文与学生答案（AI 返回中可能只包含 index/score，不重复原文）
        if (result.getItems() != null && hw != null && hw.getItems() != null) {
            Map<Integer, HomeworkItem> srcByIndex = new HashMap<>();
            for (HomeworkItem src : hw.getItems()) {
                if (src.getIndex() != null) srcByIndex.put(src.getIndex(), src);
            }
            List<ItemGradingResult> outItems = result.getItems();
            for (int i = 0; i < outItems.size(); i++) {
                ItemGradingResult ir = outItems.get(i);
                if (ir.getIndex() == null) ir.setIndex(i + 1);
                HomeworkItem src = srcByIndex.get(ir.getIndex());
                if (src != null) {
                    if (isBlank(ir.getQuestion())) ir.setQuestion(src.getQuestion());
                    if (isBlank(ir.getAnswer())) ir.setAnswer(src.getAnswer());
                    if (ir.getMaxScore() == null) ir.setMaxScore(src.getMaxScore());
                }
            }
        }

        // totalScore 兑底：仅在未返回时由 items 汇总
        if (result.getTotalScore() == null && result.getItems() != null) {
            double sum = 0;
            for (ItemGradingResult ir : result.getItems()) {
                if (ir.getScore() != null) sum += ir.getScore();
            }
            result.setTotalScore(sum);
        }

        ctx.setResult(result);
        taskStore.completeTask(ctx.getTaskId(), result);
        log.info("FeedbackStage ok: score={}/{}, items={}, taskId={}",
                result.getTotalScore(), result.getMaxScore(),
                result.getItems() == null ? 0 : result.getItems().size(),
                ctx.getTaskId());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
