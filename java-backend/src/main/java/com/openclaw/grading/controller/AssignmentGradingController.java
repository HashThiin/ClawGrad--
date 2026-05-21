package com.openclaw.grading.controller;

import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.pipeline.GradingContext.ImageAttachment;
import com.openclaw.grading.service.AssignmentGradingService;
import com.openclaw.grading.service.FileStorageService;
import com.openclaw.grading.service.GradingTaskStore;
import com.openclaw.grading.service.ModelCatalogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 通用作业批改控制器（支持全科目，文本 + 图片多模态）。
 */
@RestController
@RequestMapping("/grading")
@Slf4j
public class AssignmentGradingController {

    private final AssignmentGradingService gradingService;
    private final GradingTaskStore taskStore;
    private final FileStorageService fileStorageService;
    private final ModelCatalogService modelCatalog;

    public AssignmentGradingController(AssignmentGradingService gradingService,
                                        GradingTaskStore taskStore,
                                        FileStorageService fileStorageService,
                                        ModelCatalogService modelCatalog) {
        this.gradingService = gradingService;
        this.taskStore = taskStore;
        this.fileStorageService = fileStorageService;
        this.modelCatalog = modelCatalog;
    }

    /**
     * 获取可用模型列表（含是否支持视觉）。
     * GET /api/v1/grading/models
     */
    @GetMapping("/models")
    public ResponseEntity<List<ModelCatalogService.ModelInfo>> listModels() {
        return ResponseEntity.ok(modelCatalog.listModels());
    }

    /**
     * 提交AI智能批改任务（异步，纯文本 JSON）。
     * POST /api/v1/grading/ai-grade
     */
    @PostMapping("/ai-grade")
    public ResponseEntity<Map<String, String>> submitGradingTask(
            @RequestBody AssignmentGradingRequest request) {

        log.info("Submit grading task (json): question length={}, hasAnswer={}, model={}",
                request.getQuestion() == null ? 0 : request.getQuestion().length(),
                request.getAnswer() != null && !request.getAnswer().isBlank(),
                request.getModelId());

        String taskId = gradingService.submitGradingTask(request);

        return ResponseEntity.accepted().body(Map.of(
                "taskId", taskId,
                "status", "PROCESSING",
                "message", "批改任务已提交，请轮询查询结果"
        ));
    }

    /**
     * 提交AI智能批改任务（异步，multipart：文本 + 图片）。
     * POST /api/v1/grading/ai-grade-multipart
     * <p>表单字段：
     * <ul>
     *   <li>question: 题目（必填）</li>
     *   <li>answer:   学生答案（可选，无图片时必填）</li>
     *   <li>maxScore: 满分（可选）</li>
     *   <li>modelId:  选择的模型（可选）</li>
     *   <li>files:    多张图片（可选）</li>
     * </ul>
     */
    @PostMapping(value = "/ai-grade-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitGradingTaskMultipart(
            @RequestParam("question") String question,
            @RequestParam(value = "answer", required = false) String answer,
            @RequestParam(value = "maxScore", required = false) Double maxScore,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        log.info("Submit grading task (multipart): hasAnswer={}, model={}, files={}",
                answer != null && !answer.isBlank(), modelId,
                files == null ? 0 : files.length);

        AssignmentGradingRequest request = new AssignmentGradingRequest();
        request.setQuestion(question);
        request.setAnswer(answer);
        request.setMaxScore(maxScore == null ? 100.0 : maxScore);
        request.setModelId(modelId);

        // 预生成 taskId 作为 storage 子目录（service 内部也会生成 taskId，但这里不冲突，
        // 因为 service 调用前我们直接传图片字节，最终任务ID以 service 返回为准）
        // 这里采用：让 service 接收已构造好的 ImageAttachment 列表（不依赖于真实 taskId 路径）。
        try {
            // 临时使用一个占位目录写盘，目录命名只用于落盘归档，不影响业务taskId
            String archiveId = java.util.UUID.randomUUID().toString();
            List<ImageAttachment> images = files == null
                    ? List.of()
                    : fileStorageService.saveImages(archiveId, files);
            String taskId = gradingService.submitGradingTask(request, images);

            return ResponseEntity.accepted().body(Map.of(
                    "taskId", taskId,
                    "status", "PROCESSING",
                    "uploadedImages", images.size(),
                    "archiveId", archiveId,
                    "message", "批改任务已提交，请轮询查询结果"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Multipart grading submit failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "文件处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 查询AI批改任务状态和结果。
     * GET /api/v1/grading/ai-tasks/{taskId}
     */
    @GetMapping("/ai-tasks/{taskId}")
    public ResponseEntity<?> getAITaskResult(@PathVariable String taskId) {
        GradingTaskStore.TaskStatus task = taskStore.getTask(taskId);

        if (task == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "taskId", taskId,
                    "status", "NOT_FOUND",
                    "message", "任务不存在"
            ));
        }

        if ("COMPLETED".equals(task.getStatus())) {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("taskId", taskId);
            body.put("status", "COMPLETED");
            body.put("result", task.getResult());
            if (task.getOrganizedHomework() != null) {
                body.put("organizedHomework", task.getOrganizedHomework());
            }
            if (task.getStages() != null) {
                body.put("stages", task.getStages());
            }
            if (task.getCurrentStage() != null) {
                body.put("currentStage", task.getCurrentStage());
            }
            body.put("modelId", task.getModelId());
            body.put("modelName", task.getModelName());
            return ResponseEntity.ok(body);
        } else if ("TIMEOUT".equals(task.getStatus())) {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("taskId", taskId);
            body.put("status", "TIMEOUT");
            body.put("error", task.getError() != null ? task.getError() : "批改响应超时");
            body.put("suggestFastModel", task.isSuggestFastModel());
            if (task.getQuestion() != null) {
                body.put("question", task.getQuestion());
            }
            if (task.getAnswer() != null) {
                body.put("answer", task.getAnswer());
            }
            if (task.getOrganizedHomework() != null) {
                body.put("organizedHomework", task.getOrganizedHomework());
            }
            if (task.getStages() != null) {
                body.put("stages", task.getStages());
            }
            if (task.getCurrentStage() != null) {
                body.put("currentStage", task.getCurrentStage());
            }
            body.put("modelId", task.getModelId());
            body.put("modelName", task.getModelName());
            return ResponseEntity.ok(body);
        } else if ("FAILED".equals(task.getStatus())) {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("taskId", taskId);
            body.put("status", "FAILED");
            body.put("error", task.getError());
            if (task.getStages() != null) {
                body.put("stages", task.getStages());
            }
            if (task.getCurrentStage() != null) {
                body.put("currentStage", task.getCurrentStage());
            }
            body.put("modelId", task.getModelId());
            body.put("modelName", task.getModelName());
            return ResponseEntity.ok(body);
        } else {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("taskId", taskId);
            body.put("status", "PROCESSING");
            body.put("message", "批改进行中，请稍后再试");
            if (task.getStages() != null) {
                body.put("stages", task.getStages());
            }
            if (task.getCurrentStage() != null) {
                body.put("currentStage", task.getCurrentStage());
            }
            // PROCESSING 阶段也可能已经完成 organize，提前返回供前端展示
            if (task.getOrganizedHomework() != null) {
                body.put("organizedHomework", task.getOrganizedHomework());
            }
            body.put("modelId", task.getModelId());
            body.put("modelName", task.getModelName());
            return ResponseEntity.ok(body);
        }
    }
}
