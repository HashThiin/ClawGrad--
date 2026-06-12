package com.openclaw.grading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.model.dto.OrganizedHomework;
import com.openclaw.grading.model.entity.Submission;
import com.openclaw.grading.model.entity.User;
import com.openclaw.grading.repository.SubmissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 批改任务状态存储。
 * 当前实现落 MySQL，保留原来的 TaskStatus 读写接口供流水线和控制器复用。
 */
@Component
public class GradingTaskStore {

    private static final TypeReference<List<StageProgress>> STAGE_LIST_TYPE = new TypeReference<>() {};

    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    public GradingTaskStore(SubmissionRepository submissionRepository, ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.objectMapper = objectMapper;
    }

    public void createTask(String taskId, String question, String answer, String modelId, String modelName) {
        createTask(taskId, question, answer, modelId, modelName, null);
    }

    @Transactional
    public void createTask(String taskId, String question, String answer, String modelId, String modelName, User user) {
        Submission submission = new Submission();
        submission.setTaskId(taskId);
        submission.setUser(user);
        submission.setQuestion(question);
        submission.setAnswer(answer);
        submission.setModelId(modelId);
        submission.setModelName(modelName);
        submission.setStatus("PROCESSING");
        submission.setStagesJson(writeJson(initialStages()));
        submissionRepository.save(submission);
    }

    @Transactional
    public void stageStart(String taskId, String stageName) {
        Submission submission = findTask(taskId);
        if (submission == null) return;
        submission.setCurrentStage(stageName);
        List<StageProgress> stages = readStages(submission);
        for (StageProgress sp : stages) {
            if (sp.getName().equals(stageName)) {
                sp.setStatus("running");
                break;
            }
        }
        submission.setStagesJson(writeJson(stages));
    }

    @Transactional
    public void stageDone(String taskId, String stageName, long durationMs) {
        updateStage(taskId, stageName, "completed", durationMs);
    }

    @Transactional
    public void stageFailed(String taskId, String stageName, long durationMs) {
        updateStage(taskId, stageName, "failed", durationMs);
    }

    @Transactional
    public void completeTask(String taskId, AssignmentGradingResult result) {
        Submission submission = findTask(taskId);
        if (submission != null) {
            submission.setStatus("COMPLETED");
            submission.setResultJson(writeJson(result));
            submission.setTotalScore(result == null ? null : result.getTotalScore());
            submission.setMaxScore(result == null ? null : result.getMaxScore());
            submission.setError(null);
        }
    }

    @Transactional
    public void recordOrganized(String taskId, OrganizedHomework organized) {
        Submission submission = findTask(taskId);
        if (submission != null) {
            submission.setOrganizedHomeworkJson(writeJson(organized));
        }
    }

    @Transactional
    public void failTask(String taskId, String error) {
        Submission submission = findTask(taskId);
        if (submission != null) {
            submission.setStatus("FAILED");
            submission.setError(error);
        }
    }

    @Transactional
    public void timeoutTask(String taskId, String error, boolean suggestFastModel) {
        Submission submission = findTask(taskId);
        if (submission != null) {
            submission.setStatus("TIMEOUT");
            submission.setError(error);
            submission.setSuggestFastModel(suggestFastModel);
        }
    }

    @Transactional(readOnly = true)
    public TaskStatus getTask(String taskId) {
        return submissionRepository.findByTaskId(taskId)
                .map(this::toTaskStatus)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TaskStatus getTask(String taskId, Long userId) {
        return submissionRepository.findByTaskIdAndUserId(taskId, userId)
                .map(this::toTaskStatus)
                .orElse(null);
    }

    private void updateStage(String taskId, String stageName, String status, long durationMs) {
        Submission submission = findTask(taskId);
        if (submission == null) return;
        List<StageProgress> stages = readStages(submission);
        for (StageProgress sp : stages) {
            if (sp.getName().equals(stageName)) {
                sp.setStatus(status);
                sp.setDuration(durationMs);
                break;
            }
        }
        submission.setStagesJson(writeJson(stages));
    }

    private Submission findTask(String taskId) {
        return submissionRepository.findByTaskId(taskId).orElse(null);
    }

    private List<StageProgress> initialStages() {
        List<StageProgress> stages = new ArrayList<>();
        stages.add(new StageProgress("upload", "pending", null));
        stages.add(new StageProgress("organize", "pending", null));
        stages.add(new StageProgress("grading", "pending", null));
        stages.add(new StageProgress("feedback", "pending", null));
        return stages;
    }

    private List<StageProgress> readStages(Submission submission) {
        if (submission.getStagesJson() == null || submission.getStagesJson().isBlank()) {
            return initialStages();
        }
        try {
            return objectMapper.readValue(submission.getStagesJson(), STAGE_LIST_TYPE);
        } catch (Exception e) {
            return initialStages();
        }
    }

    private TaskStatus toTaskStatus(Submission submission) {
        TaskStatus status = new TaskStatus();
        status.setTaskId(submission.getTaskId());
        status.setQuestion(submission.getQuestion());
        status.setAnswer(submission.getAnswer());
        status.setStatus(submission.getStatus());
        status.setOrganizedHomework(readJson(submission.getOrganizedHomeworkJson(), OrganizedHomework.class));
        status.setResult(readJson(submission.getResultJson(), AssignmentGradingResult.class));
        status.setError(submission.getError());
        status.setStages(readStages(submission));
        status.setCurrentStage(submission.getCurrentStage());
        status.setSuggestFastModel(submission.isSuggestFastModel());
        status.setModelId(submission.getModelId());
        status.setModelName(submission.getModelName());
        return status;
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("任务状态序列化失败", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static class TaskStatus {
        private String taskId;
        private String question;
        private String answer;
        private String status;
        private AssignmentGradingResult result;
        private OrganizedHomework organizedHomework;
        private String error;
        private List<StageProgress> stages;
        private String currentStage;
        private boolean suggestFastModel;
        private String modelId;
        private String modelName;

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

    public static class StageProgress {
        private String name;
        private String status;
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
