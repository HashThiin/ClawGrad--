package com.openclaw.grading.pipeline.stage;

import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import com.openclaw.grading.service.ModelCatalogService;
import com.openclaw.grading.service.ModelCatalogService.ModelInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段 2：整理（选择模型 + 拼装 Prompt + 构造多模态消息）。
 * <p>1) 根据请求里的 modelId 查模型；缺省用 ModelCatalogService 默认模型；
 * <p>2) 若有图片但模型不支持 vision，则降级为纯文本批改并在 Prompt 中说明；
 * <p>3) 拼装 system / user prompt；多模态时构建 OpenAI 兼容的 content parts。
 */
@Slf4j
@Component
public class PreparationStage implements GradingStage {

    private final ModelCatalogService modelCatalog;

    public PreparationStage(ModelCatalogService modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    @Override
    public String name() {
        return "preparation";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(GradingContext ctx) {
        AssignmentGradingRequest req = ctx.getRequest();

        // 1. 选择模型
        String requested = ctx.getModelId() != null ? ctx.getModelId() : req.getModelId();
        ModelInfo model = modelCatalog.resolve(requested);
        ctx.setModelId(model.getId());

        // 2. 多模态降级判断
        if (ctx.isMultimodal() && !model.isSupportsVision()) {
            log.warn("Model {} does not support vision, fallback to text-only (will lose images)",
                    model.getId());
            ctx.setMultimodal(false);
        }

        // 3. 拼装 Prompt
        ctx.setSystemPrompt(buildSystemPrompt(ctx.isMultimodal()));
        ctx.setUserPrompt(buildUserPrompt(req));

        // 4. 多模态时，构造 OpenAI 兼容的 content parts
        if (ctx.isMultimodal()) {
            List<Object> parts = new ArrayList<>();
            // 文本部分
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", ctx.getUserPrompt());
            parts.add(textPart);
            // 图片部分（按上传顺序）
            for (GradingContext.ImageAttachment img : ctx.getImages()) {
                Map<String, Object> imgPart = new LinkedHashMap<>();
                imgPart.put("type", "image_url");
                Map<String, Object> imgUrl = new LinkedHashMap<>();
                imgUrl.put("url", img.toDataUrl());
                imgPart.put("image_url", imgUrl);
                parts.add(imgPart);
            }
            ctx.setUserContentParts(parts);
        }

        log.info("PreparationStage ok: model={}, vision={}, promptLen={}",
                model.getId(), ctx.isMultimodal(), ctx.getUserPrompt().length());
    }

    private String buildSystemPrompt(boolean multimodal) {
        StringBuilder base = new StringBuilder();
        base.append("你是一位资深教育专家AI批改助手，能批改任何科目（数学/语文/英语/物理/化学/编程/历史等）的作业。");
        base.append("你必须严格按照用户指定的JSON格式输出，不要添加任何markdown代码块标记，不要添加多余文字，只输出纯JSON。");
        
        if (multimodal) {
            base.append("用户可能提供作业的图片，请先仔细识别图片中的题目和学生答题内容，再进行批改。");
            base.append("重要：无论图片是否清晰、是否包含作业、是否能识别，你都必须返回JSON格式。");
            base.append("如果图片无法识别或不是作业，请根据你的真实判断填写JSON：");
            base.append("- totalScore: 根据实际情况给分（无法批改时给0分）");
            base.append("- feedback: 用你的真实反馈说明情况（如\"图片模糊无法识别\"、\"这是一张风景照，不包含作业内容\"等）");
            base.append("- errors: 根据实际问题填写（如\"图片质量差\"、\"内容不清晰\"、\"未检测到文字\"等）");
            base.append("绝不能因为无法批改就拒绝返回JSON，必须根据你的观察和判断填写所有字段。");
        }
        
        return base.toString();
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
                .append("5. dimensionScores 的每个维度满分必须等于题目满分（")
                .append(request.getMaxScore()).append("分），不要使用百分制\n")
                .append("6. 维度数量根据题目复杂度灵活：简单题1-2个维度即可，复杂题2-4个维度，不要硬凑\n\n");
        p.append("【输出格式 - 严格JSON，不要任何其他内容】\n")
                .append("重要：JSON键名必须严格匹配以下格式，不能有空格，不能改变大小写！\n")
                .append("必须使用 totalScore（不能写成 total Score 或 total_score），maxScore，dimensionScores 等\n\n");
        p.append("```json\n")
                .append("{\n")
                .append("  \"totalScore\": 数字,\n")
                .append("  \"maxScore\": ").append(request.getMaxScore()).append(",\n")
                .append("  \"dimensionScores\": {\"维度名\": 数字},\n")
                .append("  \"feedback\": \"总体评语，200字以内\",\n")
                .append("  \"errors\": [{\"location\":\"位置\",\"errorType\":\"错误类型\",\"description\":\"描述\",\"correction\":\"纠正方法\"}],\n")
                .append("  \"suggestions\": [\"建议1\",\"建议2\"],\n")
                .append("  \"knowledgePoints\": [{\"name\":\"知识点\",\"masteryLevel\":\"mastered/partial/weak\",\"description\":\"说明\"}],\n")
                .append("  \"reasoningSteps\": [\"分析步骤1\",\"分析步骤2\"]\n")
                .append("}\n")
                .append("```");
        return p.toString();
    }
}
