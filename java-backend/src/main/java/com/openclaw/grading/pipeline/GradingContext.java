package com.openclaw.grading.pipeline;

import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.model.dto.OrganizedHomework;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批改流水线上下文：贯穿所有 Stage，承载输入、中间产物和最终输出。
 */
@Data
public class GradingContext {

    /** 任务ID */
    private String taskId;

    /** 原始请求 */
    private AssignmentGradingRequest request;

    /** 选定的模型ID（如 dashscope/glm-5、openai/gpt-4o-mini） */
    private String modelId;

    /** 是否为多模态批改（包含图片） */
    private boolean multimodal;

    /** 图片附件（base64 + mimeType） */
    private List<ImageAttachment> images = new ArrayList<>();

    /** 整理后用于送给模型的 system prompt */
    private String systemPrompt;

    /** 整理后用于送给模型的 user prompt（文本） */
    private String userPrompt;

    /** 多模态消息内容片段（OpenAI 兼容格式） */
    private List<Object> userContentParts;

    /** Organize 阶段产物：结构化作业（题目/答案/科目） */
    private OrganizedHomework organizedHomework;

    /** 模型原始返回 */
    private String aiRawText;

    /** 解析后的最终批改结果 */
    private AssignmentGradingResult result;

    /** 失败原因 */
    private String error;

    /** 各阶段耗时（ms），用于观察流水线 */
    private Map<String, Long> stageTimings = new LinkedHashMap<>();

    /** 流水线起始时间 */
    private Instant startedAt = Instant.now();

    @Data
    public static class ImageAttachment {
        /** 文件名 */
        private String filename;
        /** MIME类型，例如 image/png */
        private String mimeType;
        /** Base64 编码（不含 data:url 前缀） */
        private String base64;

        public ImageAttachment() {}

        public ImageAttachment(String filename, String mimeType, String base64) {
            this.filename = filename;
            this.mimeType = mimeType;
            this.base64 = base64;
        }

        /** 构造 OpenAI 兼容的 data URL */
        public String toDataUrl() {
            return "data:" + (mimeType == null ? "image/png" : mimeType) + ";base64," + base64;
        }
    }
}
