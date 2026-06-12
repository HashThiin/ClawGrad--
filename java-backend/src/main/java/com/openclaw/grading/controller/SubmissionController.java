package com.openclaw.grading.controller;

import com.openclaw.grading.model.dto.SubmissionSummaryResponse;
import com.openclaw.grading.model.entity.Submission;
import com.openclaw.grading.model.entity.User;
import com.openclaw.grading.service.CurrentUserService;
import com.openclaw.grading.service.GradingTaskStore;
import com.openclaw.grading.service.SubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {
    private final CurrentUserService currentUserService;
    private final SubmissionService submissionService;
    private final GradingTaskStore taskStore;

    public SubmissionController(CurrentUserService currentUserService,
                                SubmissionService submissionService,
                                GradingTaskStore taskStore) {
        this.currentUserService = currentUserService;
        this.submissionService = submissionService;
        this.taskStore = taskStore;
    }

    @GetMapping
    public ResponseEntity<List<SubmissionSummaryResponse>> listSubmissions() {
        User user = currentUserService.requireCurrentUser();
        return ResponseEntity.ok(submissionService.listUserSubmissions(user.getId()));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> getSubmission(@PathVariable String taskId) {
        User user = currentUserService.requireCurrentUser();
        Submission submission = submissionService.requireUserSubmission(taskId, user.getId());
        GradingTaskStore.TaskStatus task = taskStore.getTask(taskId, user.getId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("status", task == null ? submission.getStatus() : task.getStatus());
        body.put("question", submission.getQuestion());
        body.put("answer", submission.getAnswer());
        body.put("modelId", submission.getModelId());
        body.put("modelName", submission.getModelName());
        body.put("totalScore", submission.getTotalScore());
        body.put("maxScore", submission.getMaxScore());
        body.put("createdAt", submission.getCreatedAt());
        body.put("updatedAt", submission.getUpdatedAt());
        if (task != null) {
            body.put("result", task.getResult());
            body.put("organizedHomework", task.getOrganizedHomework());
            body.put("stages", task.getStages());
            body.put("currentStage", task.getCurrentStage());
            body.put("error", task.getError());
            body.put("suggestFastModel", task.isSuggestFastModel());
        }
        return ResponseEntity.ok(body);
    }
}
