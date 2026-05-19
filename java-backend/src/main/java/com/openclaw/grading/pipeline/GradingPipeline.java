package com.openclaw.grading.pipeline;

import com.openclaw.grading.service.GradingTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 批改流水线编排器：把所有 {@link GradingStage} 按 order 串起来跑。
 * <p>通过 {@link Async} 注解异步执行，避免阻塞 HTTP 请求线程。
 */
@Slf4j
@Component
public class GradingPipeline {

    private final List<GradingStage> stages;
    private final GradingTaskStore taskStore;

    public GradingPipeline(List<GradingStage> stages, GradingTaskStore taskStore) {
        // 按 order 升序固定流水线顺序
        this.stages = stages.stream()
                .sorted(Comparator.comparingInt(GradingStage::order))
                .toList();
        this.taskStore = taskStore;
        log.info("GradingPipeline initialized with stages: {}",
                this.stages.stream().map(GradingStage::name).toList());
    }

    /**
     * 异步执行流水线。
     */
    @Async
    public void runAsync(GradingContext ctx) {
        run(ctx);
    }

    /**
     * 同步执行流水线（便于测试或同步接口复用）。
     */
    public void run(GradingContext ctx) {
        log.info("[Pipeline] start taskId={}, model={}, multimodal={}",
                ctx.getTaskId(), ctx.getModelId(), ctx.isMultimodal());

        // upload 阶段在 Controller 已完成（接收文件 + 创建任务），这里直接标记为 completed
        taskStore.stageDone(ctx.getTaskId(), "upload", 0L);

        for (GradingStage stage : stages) {
            Instant t0 = Instant.now();
            taskStore.stageStart(ctx.getTaskId(), stage.name());
            try {
                stage.execute(ctx);
                long cost = Duration.between(t0, Instant.now()).toMillis();
                ctx.getStageTimings().put(stage.name(), cost);
                taskStore.stageDone(ctx.getTaskId(), stage.name(), cost);
                log.info("[Pipeline] stage={} done in {}ms (taskId={})",
                        stage.name(), cost, ctx.getTaskId());
            } catch (Exception e) {
                long cost = Duration.between(t0, Instant.now()).toMillis();
                ctx.getStageTimings().put(stage.name(), cost);
                taskStore.stageFailed(ctx.getTaskId(), stage.name(), cost);
                String msg = "[" + stage.name() + "] " + e.getMessage();
                log.error("[Pipeline] stage={} FAILED in {}ms (taskId={}): {}",
                        stage.name(), cost, ctx.getTaskId(), msg, e);
                ctx.setError(msg);
                taskStore.failTask(ctx.getTaskId(), msg);
                return;
            }
        }

        log.info("[Pipeline] complete taskId={}, total stages={}, timings={}",
                ctx.getTaskId(), stages.size(), ctx.getStageTimings());
    }
}
