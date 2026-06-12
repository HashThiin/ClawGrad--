package com.openclaw.grading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String question;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String answer;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 128)
    private String modelId;

    @Column(length = 128)
    private String modelName;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String stagesJson;

    @Column(length = 32)
    private String currentStage;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String organizedHomeworkJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    private Double totalScore;

    private Double maxScore;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String error;

    private boolean suggestFastModel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
