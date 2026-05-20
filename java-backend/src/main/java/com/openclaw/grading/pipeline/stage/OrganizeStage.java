package com.openclaw.grading.pipeline.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.model.dto.HomeworkItem;
import com.openclaw.grading.model.dto.OrganizedHomework;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import com.openclaw.grading.service.ModelCatalogService;
import com.openclaw.grading.service.ModelCatalogService.ModelInfo;
import com.openclaw.grading.service.OpenClawClientService;
import com.openclaw.grading.service.GradingTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 2：整理（OpenClaw Skill 模板的 Organize 阶段）。
 * <p>职责：选择模型 + 把"原始上传"整理成结构化的 {@link OrganizedHomework}。
 * <ul>
 *   <li>纯文本场景：跳过 AI，本地直接打包 question/answer/maxScore。</li>
 *   <li>多模态场景：调用 AI 对图片做 OCR，要求返回严格的 JSON，再解析为 OrganizedHomework。</li>
 * </ul>
 * <p>注意：本阶段 <b>不</b> 在 prompt 中插入【模型选择】指令——模型选择通过请求体的 {@code model} 字段控制。
 */
@Slf4j
@Component
public class OrganizeStage implements GradingStage {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final ModelCatalogService modelCatalog;
    private final OpenClawClientService client;
    private final ObjectMapper objectMapper;
    private final GradingTaskStore taskStore;

    public OrganizeStage(ModelCatalogService modelCatalog,
                         OpenClawClientService client,
                         ObjectMapper objectMapper,
                         GradingTaskStore taskStore) {
        this.modelCatalog = modelCatalog;
        this.client = client;
        this.objectMapper = objectMapper;
        this.taskStore = taskStore;
    }

    @Override
    public String name() {
        return "organize";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(GradingContext ctx) throws Exception {
        AssignmentGradingRequest req = ctx.getRequest();

        // 1. 选择模型
        String requested = ctx.getModelId() != null ? ctx.getModelId() : req.getModelId();
        ModelInfo model = modelCatalog.resolve(requested);
        ctx.setModelId(model.getId());

        // 2. 多模态降级判断：所选模型不支持 vision 时，丢弃图片走纯文本
        if (ctx.isMultimodal() && !model.isSupportsVision()) {
            log.warn("Model {} does not support vision, fallback to text-only (will lose images)",
                    model.getId());
            ctx.setMultimodal(false);
        }

        // 3. 整理作业内容
        OrganizedHomework organized;
        if (ctx.isMultimodal()) {
            organized = organizeByAi(ctx, model.getId());
        } else {
            organized = organizeLocal(req);
        }
        ctx.setOrganizedHomework(organized);
        taskStore.recordOrganized(ctx.getTaskId(), organized);

        log.info("OrganizeStage ok: model={}, fromImage={}, subject={}, items={}",
                model.getId(), organized.isFromImage(), organized.getSubject(),
                organized.getItems() == null ? 0 : organized.getItems().size());
    }

    /** 纯文本：单题，本地打包为 items=[1题] */
    private OrganizedHomework organizeLocal(AssignmentGradingRequest req) {
        OrganizedHomework h = new OrganizedHomework();
        h.setSubject(null);
        h.setQuestion(req.getQuestion());
        h.setAnswer(req.getAnswer());
        h.setMaxScore(req.getMaxScore());
        h.setTotalMaxScore(req.getMaxScore());
        h.setFromImage(false);

        HomeworkItem item = new HomeworkItem();
        item.setIndex(1);
        item.setQuestion(req.getQuestion());
        item.setAnswer(req.getAnswer());
        item.setMaxScore(req.getMaxScore());
        h.setItems(java.util.List.of(item));
        return h;
    }

    /** 多模态：调用 AI OCR，输出 subject + items[] 多题 */
    private OrganizedHomework organizeByAi(GradingContext ctx, String modelId) throws Exception {
        String systemPrompt = buildOrganizeSystemPrompt();
        String userText = buildOrganizeUserText(ctx.getRequest());

        // 拼装多模态 content parts：text + 多张图片
        List<Object> parts = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", userText);
        parts.add(textPart);
        for (GradingContext.ImageAttachment img : ctx.getImages()) {
            Map<String, Object> imgPart = new LinkedHashMap<>();
            imgPart.put("type", "image_url");
            Map<String, Object> imgUrl = new LinkedHashMap<>();
            imgUrl.put("url", img.toDataUrl());
            imgPart.put("image_url", imgUrl);
            parts.add(imgPart);
        }

        String raw = client.chatCompletionParts(modelId, systemPrompt, parts).block();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Organize 阶段 AI 返回为空");
        }

        OrganizedHomework parsed = parseOrganizedJson(raw);
        parsed.setFromImage(true);
    
        // 满分况作：总满分以请求传入为准
        Double totalMax = ctx.getRequest().getMaxScore();
        if (totalMax == null || totalMax <= 0) totalMax = 100.0;
        parsed.setTotalMaxScore(totalMax);
        parsed.setMaxScore(totalMax);
    
        // items 兑底：如果 AI 只返了单 question/answer 没返 items，则构造一道题
        if (parsed.getItems() == null || parsed.getItems().isEmpty()) {
            HomeworkItem only = new HomeworkItem();
            only.setIndex(1);
            only.setQuestion(parsed.getQuestion() == null ? ctx.getRequest().getQuestion() : parsed.getQuestion());
            only.setAnswer(parsed.getAnswer() == null ? ctx.getRequest().getAnswer() : parsed.getAnswer());
            only.setMaxScore(totalMax);
            parsed.setItems(java.util.List.of(only));
        }
        // 均摊满分：未定义单题满分时，按题目数平均
        normalizeItemMaxScores(parsed.getItems(), totalMax);
    
        // 题号兑底：按顺序从 1 起
        for (int i = 0; i < parsed.getItems().size(); i++) {
            HomeworkItem it = parsed.getItems().get(i);
            if (it.getIndex() == null || it.getIndex() <= 0) it.setIndex(i + 1);
        }
    
        return parsed;
    }
    
    /** 如果题目未指定单题满分，按题数均摊总分 */
    private void normalizeItemMaxScores(java.util.List<HomeworkItem> items, double totalMax) {
        if (items == null || items.isEmpty()) return;
        boolean allSet = items.stream().allMatch(it -> it.getMaxScore() != null && it.getMaxScore() > 0);
        if (allSet) return;
        double per = Math.round((totalMax / items.size()) * 100.0) / 100.0;
        for (HomeworkItem it : items) {
            if (it.getMaxScore() == null || it.getMaxScore() <= 0) it.setMaxScore(per);
        }
    }

    private String buildOrganizeSystemPrompt() {
        return "你是作业图片识别助手。任务：仔细识别用户上传的作业图片，提取其中所有题目以及对应的学生答题内容。" +
                "图片中可能包含多道题，请按题目顺序拆分。" +
                "你必须只输出 JSON，不要任何 markdown 代码块、解释或多余文字。" +
                "JSON 格式：{\"subject\":\"学科\",\"items\":[{\"index\":1,\"question\":\"题目原文\",\"answer\":\"学生答案\",\"maxScore\":单题满分},...],\"remark\":\"识别备注\"}。" +
                "subject 取值如\"数学\"/\"语文\"/\"英语\"/\"物理\"/\"化学\"/\"编程\"/\"未知\"。maxScore 不确定可省略。";
    }

    private String buildOrganizeUserText(AssignmentGradingRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("请从下面的图片中识别出所有题目与对应学生答案（可能多道题），并以严格 JSON 格式输出。\n");
        if (!isBlank(req.getQuestion()) && !req.getQuestion().startsWith("(请基于上传")) {
            sb.append("【参考说明】").append(req.getQuestion()).append("\n");
        }
        if (req.getMaxScore() != null) {
            sb.append("【作业总满分】").append(req.getMaxScore()).append(" 分\n");
        }
        sb.append("【输出格式 - 严格 JSON，无任何其他内容】\n")
                .append("{\n")
                .append("  \"subject\": \"学科\",\n")
                .append("  \"items\": [\n")
                .append("    {\"index\": 1, \"question\": \"题目原文\", \"answer\": \"学生答案\", \"maxScore\": 10}\n")
                .append("  ],\n")
                .append("  \"remark\": \"识别备注\"\n")
                .append("}");
        return sb.toString();
    }

    private OrganizedHomework parseOrganizedJson(String raw) throws Exception {
        String cleaned = raw.replaceAll("(?s)```json", "")
                .replaceAll("(?s)```", "")
                .trim();
        
        // 清洗 JSON 字符串值中的非法控制字符（换行符、制表符等）
        cleaned = escapeControlCharsInJsonStringValues(cleaned);
        
        Matcher m = JSON_BLOCK.matcher(cleaned);
        if (!m.find()) {
            throw new IllegalStateException("Organize 阶段未找到 JSON 块: " +
                    (cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned));
        }
        
        String json = m.group();
        
        // 容错解析：忽略未知字段，尽量解析能识别的部分
        try {
            return objectMapper
                    .reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                    .readValue(json, OrganizedHomework.class);
        } catch (Exception e) {
            log.warn("Organize JSON parse error, attempting fallback: {}", e.getMessage());
            return parseOrganizedHomeworkFallback(json);
        }
    }
    
    /**
     * 兜底解析：当标准解析失败时，手动提取关键字段
     */
    private OrganizedHomework parseOrganizedHomeworkFallback(String json) {
        log.info("Using fallback parser for OrganizedHomework");
        OrganizedHomework hw = new OrganizedHomework();
        
        // 提取 subject
        hw.setSubject(extractStringField(json, "subject", "未知"));
        
        // 提取 items 数组（简易解析）
        List<HomeworkItem> items = extractItemsArray(json);
        hw.setItems(items);
        
        // 提取总满分
        hw.setMaxScore(extractDoubleField(json, "maxScore", 100.0));
        
        return hw;
    }
    
    /**
     * 简易提取字符串字段
     */
    private String extractStringField(String json, String fieldName, String defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\s*\"([^\"]*)\"");
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }
    
    /**
     * 简易提取数字字段
     */
    private Double extractDoubleField(String json, String fieldName, double defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\s*([0-9.]+)");
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
     * 简易提取 items 数组（使用 Jackson 树模型，支持嵌套花括号）
     */
    private List<HomeworkItem> extractItemsArray(String json) {
        List<HomeworkItem> items = new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode itemsNode = root.get("items");
            if (itemsNode == null || !itemsNode.isArray()) return items;

            int idx = 1;
            for (com.fasterxml.jackson.databind.JsonNode node : itemsNode) {
                HomeworkItem item = new HomeworkItem();
                item.setIndex(node.has("index") ? node.get("index").asInt(idx) : idx);
                item.setQuestion(node.has("question") ? node.get("question").asText("") : "");
                item.setAnswer(node.has("answer") ? node.get("answer").asText("") : "");
                item.setMaxScore(node.has("maxScore") ? node.get("maxScore").asDouble(0.0) : 0.0);
                items.add(item);
                idx++;
            }
        } catch (Exception e) {
            log.warn("Fallback items extraction also failed: {}", e.getMessage());
        }
        return items;
    }
    
    /**
     * 转义 JSON 字符串值中的控制字符（\n, \t, \r 等），避免 Jackson 解析失败。
     * 只处理字符串值内部（双引号内的内容），不破坏 JSON 结构。
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
                // 在字符串值内，转义控制字符
                if (c == '\n') {
                    result.append("\\n");
                } else if (c == '\r') {
                    result.append("\\r");
                } else if (c == '\t') {
                    result.append("\\t");
                } else if (c < 0x20) {
                    // 其他控制字符，转义为 Unicode 格式 (反斜杠 u 00XX)
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

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
