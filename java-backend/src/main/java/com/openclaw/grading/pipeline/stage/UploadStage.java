package com.openclaw.grading.pipeline.stage;

import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阶段 1：上传/输入整理。
 * <p>校验请求合法性，统一处理附件、默认值，对图片做基本校验。
 * 文件接收和落盘已经在 Controller 层完成（产生 base64 后放进 ctx.images），
 * 本阶段聚焦"对输入数据本身的校验和归一化"。
 */
@Slf4j
@Component
public class UploadStage implements GradingStage {

    /** 单图最大体积（base64 后字符串长度，约对应 4MB 原文件） */
    private static final int MAX_BASE64_LENGTH = 6_000_000;

    @Override
    public String name() {
        return "upload";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void execute(GradingContext ctx) {
        AssignmentGradingRequest req = ctx.getRequest();
        if (req == null) {
            throw new IllegalArgumentException("request is null");
        }
        
        boolean hasImages = ctx.getImages() != null && !ctx.getImages().isEmpty();
        boolean hasQuestion = !isBlank(req.getQuestion());
        boolean hasAnswer = !isBlank(req.getAnswer());
        
        // 有图片时：题目和答案都可以为空（图片中包含）
        // 无图片时：题目必填，答案必填
        if (!hasImages) {
            if (!hasQuestion) {
                throw new IllegalArgumentException("题目内容不能为空");
            }
            if (!hasAnswer) {
                throw new IllegalArgumentException("学生答案不能为空");
            }
        } else {
            // 有图片但无题目/答案，设置默认提示
            if (!hasQuestion) {
                req.setQuestion("(请基于上传的作业图片进行批改，图片中包含题目和学生答案)");
            }
            if (!hasAnswer) {
                req.setAnswer("(学生答案已包含在上传的图片中，请识别图片内容后批改)");
            }
        }
        
        // maxScore 兜底
        if (req.getMaxScore() == null || req.getMaxScore() <= 0) {
            req.setMaxScore(100.0);
        }
        
        // 图片校验
        if (hasImages) {
            for (GradingContext.ImageAttachment img : ctx.getImages()) {
                if (img.getBase64() == null || img.getBase64().isEmpty()) {
                    throw new IllegalArgumentException("图片 base64 为空: " + img.getFilename());
                }
                if (img.getBase64().length() > MAX_BASE64_LENGTH) {
                    throw new IllegalArgumentException("图片过大: " + img.getFilename());
                }
            }
            ctx.setMultimodal(true);
        }
        
        log.info("UploadStage ok: hasQuestion={}, hasAnswer={}, images={}, multimodal={}",
                hasQuestion, hasAnswer, ctx.getImages().size(), ctx.isMultimodal());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
