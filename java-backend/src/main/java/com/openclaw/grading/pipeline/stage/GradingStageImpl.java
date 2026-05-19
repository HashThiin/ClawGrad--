package com.openclaw.grading.pipeline.stage;

import com.openclaw.grading.model.dto.HomeworkItem;
import com.openclaw.grading.model.dto.OrganizedHomework;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import com.openclaw.grading.service.OpenClawClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阶段 3：批改（OpenClaw Skill 模板的 Grade 阶段）。
 * <p>一次性把全部题目打包发给 AI，要求按题号返回每题独立评分 + 整体综合分析。
 * <p>本阶段统一走 <b>纯文本</b> 接口（图片在 Organize 阶段已被 AI 转为文本）。
 */
@Slf4j
@Component
public class GradingStageImpl implements GradingStage {

    private final OpenClawClientService client;

    public GradingStageImpl(OpenClawClientService client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "grading";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void execute(GradingContext ctx) {
        OrganizedHomework hw = ctx.getOrganizedHomework();
        if (hw == null) {
            throw new IllegalStateException("OrganizedHomework 缺失（OrganizeStage 未执行）");
        }
        List<HomeworkItem> items = hw.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("OrganizedHomework.items 为空");
        }

        String systemPrompt = buildGradingSystemPrompt();
        String userPrompt = buildGradingUserPrompt(hw);

        ctx.setSystemPrompt(systemPrompt);
        ctx.setUserPrompt(userPrompt);

        String aiText = client.chatCompletion(ctx.getModelId(), systemPrompt, userPrompt).block();
        if (aiText == null || aiText.isEmpty()) {
            throw new IllegalStateException("OpenClaw 返回为空");
        }
        ctx.setAiRawText(aiText);
        log.info("GradingStage ok: model={}, subject={}, items={}, fromImage={}, aiTextLen={}",
                ctx.getModelId(), hw.getSubject(), items.size(), hw.isFromImage(), aiText.length());
    }

    private String buildGradingSystemPrompt() {
        return "你是一位资深教育专家AI批改助手，能批改任何科目（数学/语文/英语/物理/化学/编程/历史等）的整份作业。" +
                "用户会一次性提供多道题目以及学生答案，你必须对每一道题独立评分，再对整份作业给出综合分析。" +
                "你必须严格按照用户指定的 JSON 格式输出，不要添加任何 markdown 代码块标记，不要添加多余文字，只输出纯 JSON。" +
                "若某题答案缺失或无法批改，仍必须给出该题的 score（无法批改时给 0）+ feedback（说明原因）。";
    }

    private String buildGradingUserPrompt(OrganizedHomework hw) {
        Double totalMax = hw.getTotalMaxScore() == null ? 100.0 : hw.getTotalMaxScore();
        List<HomeworkItem> items = hw.getItems();

        StringBuilder p = new StringBuilder();
        if (hw.getSubject() != null && !hw.getSubject().isBlank()) {
            p.append("【学科】").append(hw.getSubject()).append("\n");
        }
        p.append("【题目总数】").append(items.size()).append(" 道\n");
        p.append("【作业总满分】").append(totalMax).append(" 分\n");
        if (hw.isFromImage()) {
            p.append("【说明】题目与答案由图片识别得到");
            if (hw.getRemark() != null && !hw.getRemark().isBlank()) {
                p.append("，识别备注：").append(hw.getRemark());
            }
            p.append("。\n");
        }
        p.append("\n【题目清单】\n");
        for (HomeworkItem it : items) {
            p.append("--- 第 ").append(it.getIndex()).append(" 题 (满分 ")
                    .append(it.getMaxScore() == null ? "?" : it.getMaxScore()).append(" 分) ---\n");
            p.append("题目：").append(safe(it.getQuestion())).append("\n");
            p.append("学生答案：").append(safe(it.getAnswer())).append("\n\n");
        }

        p.append("【批改要求】\n")
                .append("1. 按题号逐题评分，每题给出 score（不超过单题 maxScore）、feedback（评语）、errors（具体错误）、correctness（correct/partial/wrong）\n")
                .append("2. totalScore 为各题 score 之和；maxScore = ").append(totalMax).append("\n")
                .append("3. dimensionScores 为整份作业的维度评分（如准确性/规范性等），每维度满分 = 总满分\n")
                .append("4. overallSummary 200字以内，对整份作业的整体表现做综合分析\n")
                .append("5. knowledgePoints 汇总整份作业涉及的知识点掌握情况\n")
                .append("6. suggestions 给出针对整份作业的改进建议\n\n");

        p.append("【输出格式 - 严格 JSON，不要任何其他内容】\n")
                .append("```json\n")
                .append("{\n")
                .append("  \"totalScore\": 数字,\n")
                .append("  \"maxScore\": ").append(totalMax).append(",\n")
                .append("  \"dimensionScores\": {\"维度名\": 数字},\n")
                .append("  \"feedback\": \"整份作业总体评语，100字以内\",\n")
                .append("  \"overallSummary\": \"综合分析，200字以内\",\n")
                .append("  \"items\": [\n")
                .append("    {\"index\":1,\"score\":数字,\"maxScore\":数字,\"feedback\":\"单题评语\",\"correctness\":\"correct/partial/wrong\",\"errors\":[{\"location\":\"位置\",\"errorType\":\"错误类型\",\"description\":\"描述\",\"correction\":\"纠正\"}]}\n")
                .append("  ],\n")
                .append("  \"errors\": [{\"location\":\"题号或位置\",\"errorType\":\"错误类型\",\"description\":\"描述\",\"correction\":\"纠正方法\"}],\n")
                .append("  \"suggestions\": [\"建议1\",\"建议2\"],\n")
                .append("  \"knowledgePoints\": [{\"name\":\"知识点\",\"masteryLevel\":\"mastered/partial/weak\",\"description\":\"说明\"}],\n")
                .append("  \"reasoningSteps\": [\"分析步骤1\",\"分析步骤2\"]\n")
                .append("}\n")
                .append("```");
        return p.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
