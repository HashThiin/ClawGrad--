package com.openclaw.grading.controller;

import com.openclaw.grading.model.dto.CodeGradingRequest;
import com.openclaw.grading.model.dto.CodeGradingResult;
import com.openclaw.grading.model.dto.TutorRequest;
import com.openclaw.grading.model.dto.TutorResponse;
import com.openclaw.grading.service.CodeGradingService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 作业批改控制器
 * 提供编程作业批改和答疑对话的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/grading")
public class GradingController {
    
    private final CodeGradingService codeGradingService;
    
    public GradingController(CodeGradingService codeGradingService) {
        this.codeGradingService = codeGradingService;
    }
    
    /**
     * 批改编程作业（同步）
     * 
     * POST /api/v1/grading/code
     */
    @PostMapping("/code")
    public ResponseEntity<CodeGradingResult> gradeCode(@Valid @RequestBody CodeGradingRequest request) {
        log.info("Received code grading request: language={}", request.getLanguage());
        
        CodeGradingResult result = codeGradingService.gradeCode(request);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 批改编程作业（异步）
     * 适用于大批量批改场景
     * 
     * POST /api/v1/grading/code/async
     */
    @PostMapping("/code/async")
    public ResponseEntity<Map<String, String>> gradeCodeAsync(@Valid @RequestBody CodeGradingRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("Received async code grading request: taskId={}, language={}", taskId, request.getLanguage());
        
        // 异步处理
        CompletableFuture.runAsync(() -> {
            try {
                CodeGradingResult result = codeGradingService.gradeCode(request);
                // TODO: 将结果存储到数据库或 Redis，通过 taskId 查询
                log.info("Async code grading completed: taskId={}, score={}", taskId, result.getTotalScore());
            } catch (Exception e) {
                log.error("Async code grading failed: taskId={}", taskId, e);
            }
        });
        
        return ResponseEntity.accepted().body(Map.of(
                "taskId", taskId,
                "status", "processing",
                "queryUrl", "/api/v1/grading/tasks/" + taskId
        ));
    }
    
    /**
     * 查询异步任务结果
     * 
     * GET /api/v1/grading/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskResult(@PathVariable String taskId) {
        // TODO: 从数据库或 Redis 查询任务结果
        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "completed",
                "message", "Task result query - to be implemented"
        ));
    }
    
    /**
     * 健康检查
     * 
     * GET /api/v1/grading/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "openclaw-intelligent-grading",
                "version", "1.0.0"
        ));
    }
}
