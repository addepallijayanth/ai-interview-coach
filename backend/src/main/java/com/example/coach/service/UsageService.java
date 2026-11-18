package com.example.coach.service;

import com.example.coach.ai.AiCall;
import com.example.coach.ai.CostCalculator;
import com.example.coach.ai.TokenUsage;
import com.example.coach.ai.UsageTracker;
import com.example.coach.config.CoachProperties;
import com.example.coach.dto.ReportDtos.UsageResponse;
import com.example.coach.exception.AiException;
import com.example.coach.model.AiUsage;
import com.example.coach.repository.AiUsageRepository;
import com.example.coach.security.RateLimitInterceptor;
import com.example.coach.security.RateLimiter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Database-backed {@link UsageTracker}: one ledger row per Claude call, and a deployment-wide daily spend cap so
 * a public demo can never run up an unbounded bill.
 */
@Service
public class UsageService implements UsageTracker {

    private final AiUsageRepository usage;
    private final CostCalculator costCalculator;
    private final RateLimiter rateLimiter;
    private final BigDecimal dailyCap;
    private final int aiRequestsPerDay;

    public UsageService(AiUsageRepository usage, CostCalculator costCalculator, RateLimiter rateLimiter,
            CoachProperties properties) {
        this.usage = usage;
        this.costCalculator = costCalculator;
        this.rateLimiter = rateLimiter;
        this.dailyCap = properties.ai().dailySpendCapUsd();
        this.aiRequestsPerDay = properties.rateLimit().aiRequestsPerDay();
    }

    @Override
    public void checkBudget(AiCall call) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal spent = usage.totalCostSince(startOfDay);
        if (spent != null && spent.compareTo(dailyCap) >= 0) {
            throw AiException.budgetExhausted();
        }
    }

    @Override
    public void record(AiCall call, String model, TokenUsage tokens) {
        usage.save(new AiUsage(call.userId(), call.sessionId(), call.task().name(), model,
                Math.toIntExact(tokens.inputTokens()), Math.toIntExact(tokens.outputTokens()),
                Math.toIntExact(tokens.cacheReadTokens()), Math.toIntExact(tokens.cacheWriteTokens()),
                costCalculator.cost(model, tokens)));
    }

    public UsageResponse summary(long userId) {
        AiUsageRepository.Totals totals = usage.totalsForUser(userId);
        long remaining = rateLimiter.remaining(RateLimitInterceptor.AI_KEY_PREFIX + userId, aiRequestsPerDay);
        return new UsageResponse(
                orZero(totals.getCalls()),
                orZero(totals.getInputTokens()),
                orZero(totals.getOutputTokens()),
                orZero(totals.getCacheReadTokens()),
                totals.getCostUsd() == null ? BigDecimal.ZERO : totals.getCostUsd(),
                remaining,
                aiRequestsPerDay);
    }

    public BigDecimal sessionCost(long sessionId) {
        BigDecimal cost = usage.totalCostForSession(sessionId);
        return cost == null ? BigDecimal.ZERO : cost;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
