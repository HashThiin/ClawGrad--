package com.openclaw.grading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.service.GradingTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OpenClaw 批改结果回调接口
 * OpenClaw批改完成后，会将结果发送到这个接口
 */
@RestController
@RequestMapping("/callback")
@Slf4j
public class GradingCallbackController {
    
    private final GradingTaskStore taskStore;
    private final ObjectMapper objectMapper;
    
    public GradingCallbackController(GradingTaskStore taskStore, ObjectMapper objectMapper) {
        this.taskStore = taskStore;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 接收OpenClaw的批改结果回调
     * 
     * OpenClaw会在批改完成后，将结果POST到这个接口
     * 
     * 请求体示例：
     * {
     *   "taskId": "uuid",
     *   "status": "success",
     *   "result": {
     *     "totalScore": 85,
     *     "feedback": "...",
     *     ...
     *   }
     * }
     */
    @PostMapping("/grading")
    public ResponseEntity<String> handleGradingResult(@RequestBody Map<String, Object> payload) {
        log.info("Received OpenClaw grading callback: {}", payload);
        
        try {
            String taskId = (String) payload.get("taskId");
            String status = (String) payload.get("status");
            
            if ("success".equals(status)) {
                // 解析批改结果
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) payload.get("result");
                
                // 将Map转换为AssignmentGradingResult
                AssignmentGradingResult result = objectMapper.convertValue(resultMap, AssignmentGradingResult.class);
                
                // 保存结果
                taskStore.completeTask(taskId, result);
                log.info("Grading task completed: taskId={}, score={}", taskId, result.getTotalScore());
                
            } else {
                String error = (String) payload.get("error");
                taskStore.failTask(taskId, error);
                log.error("Grading task failed: taskId={}, error={}", taskId, error);
            }
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            log.error("Failed to process grading callback: {}", e.getMessage(), e);
            return ResponseEntity.ok("OK");  // 仍然返回OK，避免OpenClaw重试
        }
    }
}
