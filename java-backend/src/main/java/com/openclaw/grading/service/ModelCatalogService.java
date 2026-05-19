package com.openclaw.grading.service;

import com.openclaw.grading.config.ModelCatalogProperties;
import com.openclaw.grading.config.ModelCatalogProperties.ModelEntry;
import com.openclaw.grading.config.OpenClawGatewayProperties;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 模型目录服务：对外暴露模型清单 + 默认模型解析。
 */
@Slf4j
@Service
public class ModelCatalogService {

    private final ModelCatalogProperties catalogProps;
    private final OpenClawGatewayProperties gatewayProps;

    public ModelCatalogService(ModelCatalogProperties catalogProps,
                                OpenClawGatewayProperties gatewayProps) {
        this.catalogProps = catalogProps;
        this.gatewayProps = gatewayProps;
    }

    @PostConstruct
    public void init() {
        // 配置中没有任何模型时，至少提供一个兜底（与 gateway 默认 model 一致）
        if (catalogProps.getModels().isEmpty()) {
            ModelEntry fallback = new ModelEntry();
            fallback.setId(defaultId());
            fallback.setName(defaultId());
            fallback.setProvider("dashscope");
            fallback.setSupportsVision(false);
            fallback.setDescription("默认文本批改模型");
            catalogProps.getModels().add(fallback);
            log.warn("openclaw.models 未配置，使用兜底模型: {}", defaultId());
        }
        log.info("Model catalog loaded: defaultModel={}, total={}",
                defaultId(), catalogProps.getModels().size());
    }

    private String defaultId() {
        return catalogProps.getDefaultModel() != null
                ? catalogProps.getDefaultModel()
                : gatewayProps.getModel() != null ? gatewayProps.getModel() : "dashscope/glm-5";
    }

    /** 全部模型清单 */
    public List<ModelInfo> listModels() {
        List<ModelInfo> list = new ArrayList<>();
        for (ModelEntry e : catalogProps.getModels()) {
            list.add(ModelInfo.from(e, e.getId().equals(defaultId())));
        }
        return list;
    }

    /**
     * 根据请求中的 modelId 解析为可用模型。
     * <p>未指定 → 默认模型；
     * <p>指定但未在清单中 → 抛异常（防止任意模型id注入）。
     */
    public ModelInfo resolve(String requestedId) {
        String effective = requestedId == null || requestedId.isBlank() ? defaultId() : requestedId;
        Optional<ModelEntry> hit = catalogProps.getModels().stream()
                .filter(m -> effective.equals(m.getId()))
                .findFirst();
        if (hit.isEmpty()) {
            throw new IllegalArgumentException("未配置的模型: " + effective +
                    "（请在 openclaw.models 中维护）");
        }
        return ModelInfo.from(hit.get(), effective.equals(defaultId()));
    }

    @Data
    public static class ModelInfo {
        private String id;
        private String name;
        private String provider;
        private boolean supportsVision;
        private String description;
        private boolean isDefault;

        public static ModelInfo from(ModelEntry e, boolean isDefault) {
            ModelInfo m = new ModelInfo();
            m.setId(e.getId());
            m.setName(e.getName() == null ? e.getId() : e.getName());
            m.setProvider(e.getProvider());
            m.setSupportsVision(e.isSupportsVision());
            m.setDescription(e.getDescription());
            m.setDefault(isDefault);
            return m;
        }
    }
}
