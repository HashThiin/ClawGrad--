package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.pipeline.GradingContext;
import com.openclaw.grading.pipeline.GradingContext.ImageAttachment;
import com.openclaw.grading.pipeline.GradingPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 通用作业批改服务（薄入口）。
 * <p>仅负责创建任务、构造上下文、触发流水线异步执行。
 * <p>真正的批改逻辑由 {@link GradingPipeline} 中的各个 Stage 组合完成。
 */
@Service
@Slf4j
public class AssignmentGradingService {

    private final GradingTaskStore taskStore;
    private final GradingPipeline pipeline;
    private final ModelCatalogService modelCatalog;

    public AssignmentGradingService(GradingTaskStore taskStore, GradingPipeline pipeline,
                                     ModelCatalogService modelCatalog) {
        this.taskStore = taskStore;
        this.pipeline = pipeline;
        this.modelCatalog = modelCatalog;
    }

    /**
     * 提交批改任务（异步）。
     * <p>立即返回 taskId，后台 pipeline 异步执行：upload → preparation → grading → feedback。
     */
    public String submitGradingTask(AssignmentGradingRequest request) {
        return submitGradingTask(request, List.of());
    }

    /**
     * 带图片附件的提交。
     */
    public String submitGradingTask(AssignmentGradingRequest request, List<ImageAttachment> images) {
        String taskId = UUID.randomUUID().toString();
        log.info("Submit grading task: taskId={}, model={}, images={}",
                taskId, request.getModelId(), images == null ? 0 : images.size());

        // 解析模型ID和展示名
        String modelId = request.getModelId();
        String modelName = modelId;
        try {
            ModelCatalogService.ModelInfo info = modelCatalog.resolve(modelId);
            modelId = info.getId();
            modelName = info.getName();
        } catch (Exception e) {
            log.warn("Cannot resolve model '{}', use raw id as display name", modelId);
        }

        taskStore.createTask(taskId, request.getQuestion(), request.getAnswer(), modelId, modelName);

        GradingContext ctx = new GradingContext();
        ctx.setTaskId(taskId);
        ctx.setRequest(request);
        ctx.setModelId(request.getModelId());
        if (images != null && !images.isEmpty()) {
            ctx.getImages().addAll(images);
            ctx.setMultimodal(true);
        }

        // 异步触发流水线（@Async）
        pipeline.runAsync(ctx);
        return taskId;
    }
}
