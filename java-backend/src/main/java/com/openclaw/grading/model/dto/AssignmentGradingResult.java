package com.openclaw.grading.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 通用作业批改结果（支持全科目）
 */
@Data
public class AssignmentGradingResult {
    
    /**
     * 总分
     */
    @JsonProperty("totalScore")
    @JsonAlias({"total Score", "TotalScore", "total_score"})
    private Double totalScore;
    
    /**
     * 满分
     */
    private Double maxScore;
    
    /**
     * 各维度得分（不同科目维度不同）
     * 例如：
     * - 代码：correctness, readability, efficiency
     * - 作文：content, structure, language, creativity
     * - 数学：accuracy, process, conclusion
     * - 英语：grammar, vocabulary, comprehension, writing
     */
    private Map<String, Double> dimensionScores;
    
    /**
     * 批改详情/评语
     */
    private String feedback;
    
    /**
     * 错误点列表
     */
    private List<ErrorPoint> errors;
    
    /**
     * 改进建议
     */
    private List<String> suggestions;
    
    /**
     * 知识点掌握情况
     */
    private List<KnowledgePoint> knowledgePoints;
    
    /**
     * 详细分析步骤
     */
    private List<String> reasoningSteps;

    /**
     * 逐题批改明细（多题作业时使用；单题可为空）
     */
    private List<ItemGradingResult> items;

    /**
     * 总体总结（多题下的综合分析）
     */
    private String overallSummary;
    
    @Data
    public static class ErrorPoint {
        /**
         * 错误位置（行号/段落/题号）
         */
        private String location;
        
        /**
         * 错误类型
         */
        private String errorType;
        
        /**
         * 错误描述
         */
        private String description;
        
        /**
         * 正确答案/建议
         */
        private String correction;
    }
    
    @Data
    public static class KnowledgePoint {
        /**
         * 知识点名称
         */
        private String name;
        
        /**
         * 掌握程度：mastered(掌握), partial(部分掌握), weak(薄弱)
         */
        private String masteryLevel;
        
        /**
         * 说明
         */
        private String description;
    }
}
