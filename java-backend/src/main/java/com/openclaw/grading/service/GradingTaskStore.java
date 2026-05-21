package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.model.dto.OrganizedHomework;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批改任务状态存储（内存版）
 * 生产环境建议用Redis
 */
@Component
public class GradingTaskStore {
    
    private final Map<String, TaskStatus> taskStore = new ConcurrentHashMap<>();
    
    public void createTask(String taskId, String question, String answer, String modelId, String modelName) {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setQuestion(question);
        status.setAnswer(answer);
        status.setModelId(modelId);
        status.setModelName(modelName);
        status.setStatus("PROCESSING");
        // 预初始化 4 阶段进度
        List<StageProgress> stages = new ArrayList<>();
        stages.add(new StageProgress("upload", "pending", null));
        stages.add(new StageProgress("organize", "pending", null));
        stages.add(new StageProgress("grading", "pending", null));
        stages.add(new StageProgress("feedback", "pending", null));
        status.setStages(stages);
        taskStore.put(taskId, status);
    }

    /** 阶段开始（进入 running） */
    public void stageStart(String taskId, String stageName) {
        TaskStatus status = taskStore.get(taskId);
        if (status == null || status.getStages() == null) return;
        status.setCurrentStage(stageName);
        for (StageProgress sp : status.getStages()) {
            if (sp.getName().equals(stageName)) {
                sp.setStatus("running");
                break;
            }
        }
    }

    /** 阶段成功完成 */
    public void stageDone(String taskId, String stageName, long durationMs) {
        TaskStatus status = taskStore.get(taskId);
        if (status == null || status.getStages() == null) return;
        for (StageProgress sp : status.getStages()) {
            if (sp.getName().equals(stageName)) {
                sp.setStatus("completed");
                sp.setDuration(durationMs);
                break;
            }
        }
    }

    /** 阶段失败 */
    public void stageFailed(String taskId, String stageName, long durationMs) {
        TaskStatus status = taskStore.get(taskId);
        if (status == null || status.getStages() == null) return;
        for (StageProgress sp : status.getStages()) {
            if (sp.getName().equals(stageName)) {
                sp.setStatus("failed");
                sp.setDuration(durationMs);
                break;
            }
        }
    }
    
    public void completeTask(String taskId, AssignmentGradingResult result) {
        TaskStatus status = taskStore.get(taskId);
        if (status != null) {
            status.setStatus("COMPLETED");
            status.setResult(result);
        }
    }

    /** 记录 Organize 阶段产物，供查询接口返回。 */
    public void recordOrganized(String taskId, OrganizedHomework organized) {
        TaskStatus status = taskStore.get(taskId);
        if (status != null) {
            status.setOrganizedHomework(organized);
        }
    }
    
    public void failTask(String taskId, String error) {
        TaskStatus status = taskStore.get(taskId);
        if (status != null) {
            status.setStatus("FAILED");
            status.setError(error);
        }
    }

    /** 任务超时（非失败，提供重试选项） */
    public void timeoutTask(String taskId, String error, boolean suggestFastModel) {
        TaskStatus status = taskStore.get(taskId);
        if (status != null) {
            status.setStatus("TIMEOUT");
            status.setError(error);
            status.setSuggestFastModel(suggestFastModel);
        }
    }
    
    public TaskStatus getTask(String taskId) {
        return taskStore.get(taskId);
    }
    
    public static class TaskStatus {
        private String taskId;
        private String question;
        private String answer;
        private String status;  // PROCESSING, COMPLETED, FAILED, TIMEOUT
        private AssignmentGradingResult result;
        private OrganizedHomework organizedHomework;
        private String error;
        private List<StageProgress> stages;
        private String currentStage;
        /** 超时时建议切换快速模型 */
        private boolean suggestFastModel;
        /** 使用的模型ID */
        private String modelId;
        /** 使用的模型展示名 */
        private String modelName;
        
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
        
        public OrganizedHomework getOrganizedHomework() { return organizedHomework; }
        public void setOrganizedHomework(OrganizedHomework organizedHomework) { this.organizedHomework = organizedHomework; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public List<StageProgress> getStages() { return stages; }
        public void setStages(List<StageProgress> stages) { this.stages = stages; }
        
        public String getCurrentStage() { return currentStage; }
        public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
        
        public boolean isSuggestFastModel() { return suggestFastModel; }
        public void setSuggestFastModel(boolean suggestFastModel) { this.suggestFastModel = suggestFastModel; }

        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
    }

    /** 单个阶段的进度 */
    public static class StageProgress {
        private String name;
        /** pending / running / completed / failed */
        private String status;
        /** 耗时，单位 ms，完成后才有值 */
        private Long duration;

        public StageProgress() {}
        public StageProgress(String name, String status, Long duration) {
            this.name = name;
            this.status = status;
            this.duration = duration;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getDuration() { return duration; }
        public void setDuration(Long duration) { this.duration = duration; }
    }
}
