package com.openclaw.grading.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 整理阶段的产物：结构化的作业内容。
 * <p>纯文本场景：直接由 AssignmentGradingRequest 本地打包；
 * <p>多模态场景：由 AI 对图片做 OCR 后返回结构化 JSON 解析得到。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizedHomework {

    /** 学科（语文/数学/英语/物理/化学/编程/历史/未知 等） */
    @JsonAlias({"科目", "subject_name"})
    private String subject;

    /** 题目内容（纯文本） */
    @JsonAlias({"题目", "question_text"})
    private String question;

    /** 学生答案（纯文本） */
    @JsonAlias({"答案", "学生答案", "student_answer"})
    private String answer;

    /** 满分 */
    @JsonAlias({"max_score", "满分"})
    private Double maxScore;

    /** 题目列表（单题也统一用 items，元素数 = 1） */
    @JsonAlias({"题目列表", "questions"})
    private List<HomeworkItem> items;

    /** 总满分（所有题满分之和，可能由请求传入） */
    @JsonAlias({"total_max_score", "总满分"})
    private Double totalMaxScore;

    /** 是否来自图片 OCR（true=多模态来源，false=纯文本来源） */
    private boolean fromImage;

    /** 整理后的备注/说明（图片识别置信度低时可写入） */
    @JsonAlias({"备注", "notes"})
    private String remark;
}
