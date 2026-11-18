package com.example.coach.ai;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Virtual-thread executor for blocking AI work: parallel extraction calls and SSE agent runs.
 * Deliberately not exposed as an {@code Executor} bean so it never replaces Spring's own task executor.
 */
@Component
public class AiExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
