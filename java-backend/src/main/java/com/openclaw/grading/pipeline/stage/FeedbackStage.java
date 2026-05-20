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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 4：反馈整理（解析 AI 返回 + 写入任务库）�?
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
        
        AssignmentGradingResult result;
        try {
            // 容错解析：忽略未知字段，尽量解析能识别的部分
            result = objectMapper
                    .reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                    .readValue(json, AssignmentGradingResult.class);
        } catch (Exception e) {
            log.warn("Feedback JSON parse error, attempting fallback: {}", e.getMessage());
            result = parseGradingResultFallback(json, ctx);
        }

        OrganizedHomework hw = ctx.getOrganizedHomework();
        Double totalMax = hw != null && hw.getTotalMaxScore() != null
                ? hw.getTotalMaxScore()
                : ctx.getRequest().getMaxScore();
        if (result.getMaxScore() == null) {
            result.setMaxScore(totalMax);
        }

        // 回填逐题题目原文与学生答案（AI 返回中可能只包含 index/score，不重复原文�?
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

        // totalScore 兑底：仅在未返回时由 items 汇�?
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

    /**
     * 兜底校验：确保 totalScore 不超过 maxScore，各 item score 不超 maxScore
     */
    private void sanitizeScores(AssignmentGradingResult result) {
        Double max = result.getMaxScore();
        if (max == null) return;
        // 限制总分
        if (result.getTotalScore() != null && result.getTotalScore() > max) {
            log.warn("totalScore {} capped to maxScore {}", result.getTotalScore(), max);
            result.setTotalScore(max);
        }
        // 限制各题分数
        if (result.getItems() != null) {
            for (ItemGradingResult item : result.getItems()) {
                Double itemMax = item.getMaxScore();
                if (item.getScore() != null && itemMax != null && item.getScore() > itemMax) {
                    log.warn("item[{}] score {} capped to maxScore {}", item.getIndex(), item.getScore(), itemMax);
                    item.setScore(itemMax);
                }
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * �?AI 文本中抽取第一�?JSON（容�?markdown 代码块和前后说明�?
     * 同时清理 JSON 中的非法字符（换行符、控制字符等�?
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalStateException("AI 返回为空");
        }
        String cleaned = text.replaceAll("(?s)```json", "")
                .replaceAll("(?s)```", "")
                .trim();
        
        // 清洗字符串值中的控制字�?
        cleaned = escapeControlCharsInJsonStringValues(cleaned);
        
        Matcher m = JSON_BLOCK.matcher(cleaned);
        if (m.find()) {
            String json = m.group();
            // 清理 JSON 键名中的非法字符（空格、换行符、制表符等）
            // 例如�?total Score" -> "totalScore", "total\nScore" -> "totalScore"
            json = cleanJsonKeys(json);
            return json;
        }
        throw new IllegalStateException("未在AI返回中找到JSON�? " +
                (cleaned.length() > 500 ? cleaned.substring(0, 500) + "..." : cleaned));
    }

    /**
     * 清理 JSON 键名中的空白字符（空格、换行、制表符等）
     */
    private String cleanJsonKeys(String json) {
        // 匹配 "xxx yyy" 模式的键名（中间包含空白字符�?
        Pattern keyPattern = Pattern.compile("\"([^\"]*[\\s\\n\\r\\t][^\"]*)\"");
        Matcher matcher = keyPattern.matcher(json);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String originalKey = matcher.group(1);
            // 移除所有空白字�?
            String cleanedKey = originalKey.replaceAll("[\\s\\n\\r\\t]+", "");
            matcher.appendReplacement(sb, "\"" + cleanedKey + "\"");
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 转义 JSON 字符串值中的控制字符（\n, \t, \r 等），避�?Jackson 解析失败�?
     * 只处理字符串值内部（双引号内的内容），不破坏 JSON 结构�?
     */
    private String escapeControlCharsInJsonStringValues(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                result.append(c);
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                result.append(c);
                inString = !inString;
                continue;
            }
            
            if (inString) {
                // 在字符串值内，转义控制字�?
                if (c == '\n') {
                    result.append("\\n");
                } else if (c == '\r') {
                    result.append("\\r");
                } else if (c == '\t') {
                    result.append("\\t");
                } else if (c < 0x20) {
                    // 其他控制字符，转义为 Unicode 格式 (反斜�?u 00XX)
                    result.append(String.format("\\u%04x", (int) c));
                } else {
                    result.append(c);
                }
            } else {
                // 在字符串外（JSON 结构部分），直接保留
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 兜底解析：当标准解析失败时，手动提取关键字段
     */
    private AssignmentGradingResult parseGradingResultFallback(String json, GradingContext ctx) {
        log.info("Using fallback parser for AssignmentGradingResult");
        AssignmentGradingResult result = new AssignmentGradingResult();
        
        // 提取总分
        result.setTotalScore(extractDoubleField(json, "totalScore", 0.0));
        result.setMaxScore(extractDoubleField(json, "maxScore", 
                ctx.getRequest().getMaxScore() != null ? ctx.getRequest().getMaxScore() : 100.0));
        
        // 提取总体反馈
        result.setFeedback(extractStringField(json, "feedback", ""));
        result.setOverallSummary(extractStringField(json, "overallSummary", ""));
        
        // 提取 items 数组
        List<ItemGradingResult> items = extractItemsArray(json);
        result.setItems(items);
        
        // 如果 totalScore 未提取到，从 items 计算
        if (result.getTotalScore() == null && !items.isEmpty()) {
            double sum = items.stream()
                    .mapToDouble(item -> item.getScore() != null ? item.getScore() : 0.0)
                    .sum();
            result.setTotalScore(sum);
        }
        
        return result;
    }
    
    /**
     * 简易提取字符串字段
     */
    private String extractStringField(String json, String fieldName, String defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }
        
    /**
     * 简易提取数字字�?
     */
    private Double extractDoubleField(String json, String fieldName, double defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
        
    /**
     * 简易提�?items 数组（批改结果）
     */
    private List<ItemGradingResult> extractItemsArray(String json) {
        List<ItemGradingResult> items = new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode itemsNode = root.get("items");
            if (itemsNode == null || !itemsNode.isArray()) return items;

            for (com.fasterxml.jackson.databind.JsonNode node : itemsNode) {
                ItemGradingResult item = new ItemGradingResult();
                item.setIndex(node.has("index") ? node.get("index").asInt(0) : 0);
                item.setScore(node.has("score") ? node.get("score").asDouble(0.0) : 0.0);
                item.setMaxScore(node.has("maxScore") ? node.get("maxScore").asDouble(0.0) : 0.0);
                item.setFeedback(node.has("feedback") ? node.get("feedback").asText("") : "");
                item.setCorrectness(node.has("correctness") ? node.get("correctness").asText("partial") : "partial");
                item.setQuestion(node.has("question") ? node.get("question").asText("") : "");
                item.setAnswer(node.has("answer") ? node.get("answer").asText("") : "");
                items.add(item);
            }
        } catch (Exception e) {
            log.warn("Fallback items extraction via JsonNode also failed: {}, trying regex fallback...", e.getMessage());
            items = extractItemsViaRegex(json);
        }
        return items;
    }

    /**
     * 第三层兜底：当 readTree 也失败时，用正则逐项提取 items
     * 适用于 JSON 结构部分损坏但 item 对象本身完整的情况
     */
    private List<ItemGradingResult> extractItemsViaRegex(String json) {
        List<ItemGradingResult> items = new ArrayList<>();
        try {
            // 1. 找出 "items":[...] 区域
            Matcher regionMatcher = Pattern.compile("\"items\"\\s*:\\s*\\[(.*?)(?:\\]|$)", Pattern.DOTALL).matcher(json);
            if (!regionMatcher.find()) {
                log.warn("Regex fallback: could not find items section");
                return items;
            }
            String region = regionMatcher.group(1);

            // 2. 用大括号匹配逐项提取
            int braceDepth = 0;
            int objStart = -1;
            for (int i = 0; i < region.length(); i++) {
                char c = region.charAt(i);
                if (c == '{') {
                    if (braceDepth == 0) objStart = i;
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0 && objStart >= 0) {
                        String objStr = region.substring(objStart, i + 1);
                        ItemGradingResult item = parseItemObject(objStr);
                        if (item != null) {
                            items.add(item);
                        }
                        objStart = -1;
                    }
                }
            }
            log.info("Regex fallback extracted {} items", items.size());
        } catch (Exception e) {
            log.warn("Regex fallback also failed: {}", e.getMessage());
        }
        return items;
    }

    /**
     * 从单个 item JSON 对象字符串中提取字段
     */
    private ItemGradingResult parseItemObject(String objStr) {
        try {
            ItemGradingResult item = new ItemGradingResult();
            item.setIndex(extractIntField(objStr, "index", 0));
            item.setScore(extractDoubleField(objStr, "score", 0.0));
            item.setMaxScore(extractDoubleField(objStr, "maxScore", 0.0));
            item.setFeedback(extractStringField(objStr, "feedback", ""));
            item.setCorrectness(extractStringField(objStr, "correctness", "partial"));
            item.setQuestion(extractStringField(objStr, "question", ""));
            item.setAnswer(extractStringField(objStr, "answer", ""));
            if (item.getIndex() > 0 || item.getScore() != null) {
                return item;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 简易提取整数字段
     */
    private Integer extractIntField(String json, String fieldName, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

