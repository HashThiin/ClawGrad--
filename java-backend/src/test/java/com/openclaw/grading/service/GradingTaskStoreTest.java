package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.AssignmentGradingResult;
import com.openclaw.grading.model.dto.OrganizedHomework;
import com.openclaw.grading.model.entity.User;
import com.openclaw.grading.repository.SubmissionRepository;
import com.openclaw.grading.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GradingTaskStoreTest {

    @Autowired
    private GradingTaskStore taskStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private User user;

    @BeforeEach
    void setUp() {
        submissionRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("student1");
        user.setPasswordHash("encoded-password");
        user.setDisplayName("Student One");
        user.setRole("STUDENT");
        user = userRepository.save(user);
    }

    @Test
    void persistsTaskLifecycle() {
        taskStore.createTask("task-1", "1+1=?", "2", "model-a", "Model A", user);

        GradingTaskStore.TaskStatus created = taskStore.getTask("task-1", user.getId());
        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo("PROCESSING");
        assertThat(created.getStages()).hasSize(4);

        taskStore.stageStart("task-1", "grading");
        taskStore.stageDone("task-1", "grading", 120);

        OrganizedHomework organized = new OrganizedHomework();
        organized.setQuestion("1+1=?");
        organized.setAnswer("2");
        organized.setFromImage(false);
        taskStore.recordOrganized("task-1", organized);

        AssignmentGradingResult result = new AssignmentGradingResult();
        result.setTotalScore(10.0);
        result.setMaxScore(10.0);
        result.setFeedback("Good");
        taskStore.completeTask("task-1", result);

        GradingTaskStore.TaskStatus completed = taskStore.getTask("task-1", user.getId());
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getResult().getTotalScore()).isEqualTo(10.0);
        assertThat(completed.getOrganizedHomework().getQuestion()).isEqualTo("1+1=?");
        assertThat(completed.getStages()).anySatisfy(stage -> {
            assertThat(stage.getName()).isEqualTo("grading");
            assertThat(stage.getStatus()).isEqualTo("completed");
            assertThat(stage.getDuration()).isEqualTo(120);
        });
    }

    @Test
    void doesNotReturnOtherUsersTask() {
        User other = new User();
        other.setUsername("student2");
        other.setPasswordHash("encoded-password");
        other.setDisplayName("Student Two");
        other.setRole("STUDENT");
        other = userRepository.save(other);

        taskStore.createTask("task-1", "1+1=?", "2", "model-a", "Model A", user);

        assertThat(taskStore.getTask("task-1", other.getId())).isNull();
    }
}
