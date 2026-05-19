package com.openclaw.grading.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 单题批改结果（多题作业中的逐题明细）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemGradingResult {

    /** 题号（从 1 开始） */
    private Integer index;

    /** 题目（冗余存储便于前端直接展示） */
    private String question;

    /** 学生答案（冗余存储） */
    private String answer;

    /** 单题得分 */
    @JsonAlias({"totalScore", "score_value"})
    private Double score;

    /** 单题满分 */
    private Double maxScore;

    /** 单题评语 */
    private String feedback;

    /** 错误点 */
    private List<AssignmentGradingResult.ErrorPoint> errors;

    /** 单题正确性：correct / partial / wrong */
    private String correctness;
}
