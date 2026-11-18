package com.example.coach.ai;

/**
 * Budget guard and usage ledger for Claude calls. The production implementation is backed by the database
 * ({@code UsageService}); the AI eval uses an in-memory one.
 */
public interface UsageTracker {

    /** Throws {@code AiException.budgetExhausted()} when the daily spend cap has been reached. */
    void checkBudget(AiCall call);

    void record(AiCall call, String model, TokenUsage usage);
}
