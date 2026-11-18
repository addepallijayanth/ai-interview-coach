package com.example.coach.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Ledger row for one Claude API call. */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, length = 40)
    private String task;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private int cacheReadTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private int cacheWriteTokens;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiUsage() {}

    public AiUsage(Long userId, Long sessionId, String task, String model, int inputTokens, int outputTokens,
            int cacheReadTokens, int cacheWriteTokens, BigDecimal costUsd) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.task = task;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.costUsd = costUsd;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public String getTask() {
        return task;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getCacheReadTokens() {
        return cacheReadTokens;
    }

    public int getCacheWriteTokens() {
        return cacheWriteTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
