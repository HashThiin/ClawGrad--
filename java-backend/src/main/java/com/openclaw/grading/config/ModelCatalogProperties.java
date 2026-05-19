package com.openclaw.grading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型目录配置：维护可切换的大模型清单。
 * <p>对应 application.yml 中的 openclaw.models.* 节点。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openclaw")
public class ModelCatalogProperties {

    /** 可用模型列表 */
    private List<ModelEntry> models = new ArrayList<>();

    /** 缺省模型id（未指定时使用） */
    private String defaultModel;

    @Data
    public static class ModelEntry {
        /** 模型id（OpenClaw 路由用，如 dashscope/glm-5、openai/gpt-4o-mini） */
        private String id;
        /** 展示名称 */
        private String name;
        /** 模型提供方（dashscope / openai / anthropic …） */
        private String provider;
        /** 是否支持视觉/多模态 */
        private boolean supportsVision = false;
        /** 描述 */
        private String description;
    }
}
