package com.openclaw.grading.controller;

import com.openclaw.grading.model.dto.AssignmentGradingRequest;
import com.openclaw.grading.service.AssignmentGradingService;
import com.openclaw.grading.service.GradingTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通用作业批改控制器（支持全科目）
 */
@RestController
@RequestMapping("/grading")
@Slf4j
public class AssignmentGradingController {
    
    private final AssignmentGradingService gradingService;
    private final GradingTaskStore taskStore;
    
    public AssignmentGradingController(AssignmentGradingService gradingService, GradingTaskStore taskStore) {
        this.gradingService = gradingService;
        this.taskStore = taskStore;
    }
    
    /**
     * 提交AI智能批改任务（异步）
     * 
     * 流程：
     * 1. 接收题目+答案
     * 2. 生成taskId并保存任务状态
     * 3. 发送给OpenClaw Gateway（带callbackUrl）
     * 4. 立即返回taskId给前端
     * 5. OpenClaw批改完成后回调我们的接口
     * 6. 前端轮询 /tasks/{taskId} 获取结果
     */
    @PostMapping("/ai-grade")
    public ResponseEntity<Map<String, String>> submitGradingTask(
            @RequestBody AssignmentGradingRequest request) {
        
        log.info("Submit grading task: question={}, answer length={}", 
                request.getQuestion(), request.getAnswer().length());
        
        // 生成任务ID并保存
        String taskId = gradingService.submitGradingTask(request);
        
        return ResponseEntity.accepted().body(Map.of(
                "taskId", taskId,
                "status", "PROCESSING",
                "message", "批改任务已提交，请轮询查询结果"
        ));
    }
    
    /**
     * 查询AI批改任务状态和结果
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
            return ResponseEntity.ok(Map.of(
                    "taskId", taskId,
                    "status", "COMPLETED",
                    "result", task.getResult()
            ));
        } else if ("FAILED".equals(task.getStatus())) {
            return ResponseEntity.ok(Map.of(
                    "taskId", taskId,
                    "status", "FAILED",
                    "error", task.getError()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "taskId", taskId,
                    "status", "PROCESSING",
                    "message", "批改进行中，请稍后再试"
            ));
        }
    }
}
