package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.AssignmentGradingResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批改任务状态存储（内存版）
 * 生产环境建议用Redis
 */
@Component
public class GradingTaskStore {
    
    private final Map<String, TaskStatus> taskStore = new ConcurrentHashMap<>();
    
    public void createTask(String taskId, String question, String answer) {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setQuestion(question);
        status.setAnswer(answer);
        status.setStatus("PROCESSING");
        taskStore.put(taskId, status);
    }
    
    public void completeTask(String taskId, AssignmentGradingResult result) {
        TaskStatus status = taskStore.get(taskId);
        if (status != null) {
            status.setStatus("COMPLETED");
            status.setResult(result);
        }
    }
    
    public void failTask(String taskId, String error) {
        TaskStatus status = taskStore.get(taskId);
        if (status != null) {
            status.setStatus("FAILED");
            status.setError(error);
        }
    }
    
    public TaskStatus getTask(String taskId) {
        return taskStore.get(taskId);
    }
    
    public static class TaskStatus {
        private String taskId;
        private String question;
        private String answer;
        private String status;  // PROCESSING, COMPLETED, FAILED
        private AssignmentGradingResult result;
        private String error;
        
        // Getters and Setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public AssignmentGradingResult getResult() { return result; }
        public void setResult(AssignmentGradingResult result) { this.result = result; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
