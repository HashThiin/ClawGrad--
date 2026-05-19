package com.openclaw.grading.pipeline;

/**
 * 批改流水线阶段统一接口。
 * <p>实现类通过 Spring 自动装配进 {@link GradingPipeline}，按 {@link #order()} 升序执行。
 */
public interface GradingStage {

    /** 阶段名（用于日志和耗时统计） */
    String name();

    /** 执行顺序，越小越先执行 */
    default int order() {
        return 100;
    }

    /**
     * 执行阶段逻辑。
     * <p>本阶段失败应抛出异常，由 {@link GradingPipeline} 统一捕获并写入 {@link GradingContext#getError()}。
     */
    void execute(GradingContext ctx) throws Exception;
}
