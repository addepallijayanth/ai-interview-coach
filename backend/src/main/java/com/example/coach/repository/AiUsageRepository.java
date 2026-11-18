package com.example.coach.repository;

import com.example.coach.model.AiUsage;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    /** Projection for aggregate usage numbers. */
    interface Totals {
        Long getCalls();

        Long getInputTokens();

        Long getOutputTokens();

        Long getCacheReadTokens();

        BigDecimal getCostUsd();
    }

    @Query("select sum(u.costUsd) from AiUsage u where u.createdAt >= :since")
    BigDecimal totalCostSince(@Param("since") Instant since);

    @Query("""
            select count(u) as calls, sum(u.inputTokens) as inputTokens, sum(u.outputTokens) as outputTokens,
                   sum(u.cacheReadTokens) as cacheReadTokens, sum(u.costUsd) as costUsd
            from AiUsage u where u.userId = :userId""")
    Totals totalsForUser(@Param("userId") Long userId);

    @Query("select sum(u.costUsd) from AiUsage u where u.sessionId = :sessionId")
    BigDecimal totalCostForSession(@Param("sessionId") Long sessionId);
}
