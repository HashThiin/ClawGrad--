package com.openclaw.grading.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 单道题目（图片多题场景由 OCR 提取，纯文本场景由请求直接构造）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeworkItem {

    /** 题号（从 1 开始） */
    @JsonAlias({"index", "no", "题号"})
    private Integer index;

    /** 题目内容 */
    @JsonAlias({"题目", "question_text"})
    private String question;

    /** 学生答案 */
    @JsonAlias({"答案", "学生答案", "student_answer"})
    private String answer;

    /** 单题满分（可选；缺省由调用方按总分均摊） */
    @JsonAlias({"max_score", "满分"})
    private Double maxScore;
}
