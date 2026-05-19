package com.openclaw.grading.pipeline.stage;

import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import com.openclaw.grading.service.OpenClawClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阶段 3：调用 AI 进行批改。
 * <p>同步阻塞地拿到模型返回（pipeline 自身是异步执行的，此处阻塞 OK）。
 * <p>多模态 → chatCompletionParts；纯文本 → chatCompletion。
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
        String aiText;
        if (ctx.isMultimodal()) {
            aiText = client.chatCompletionParts(ctx.getModelId(), ctx.getSystemPrompt(),
                            ctx.getUserContentParts())
                    .block();
        } else {
            aiText = client.chatCompletion(ctx.getModelId(), ctx.getSystemPrompt(),
                            ctx.getUserPrompt())
                    .block();
        }
        if (aiText == null || aiText.isEmpty()) {
            throw new IllegalStateException("OpenClaw 返回为空");
        }
        ctx.setAiRawText(aiText);
        log.info("GradingStage ok: aiText length={}", aiText.length());
    }
}
