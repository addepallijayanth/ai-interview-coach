package com.example.coach.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "jd_id", nullable = false)
    private Long jdId;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "model_used", nullable = false, length = 200)
    private String modelUsed;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Analysis() {}

    public Analysis(Long userId, Long resumeId, Long jdId, int matchScore, String resultJson, String modelUsed,
            int tokensIn, int tokensOut, BigDecimal costUsd) {
        this.userId = userId;
        this.resumeId = resumeId;
        this.jdId = jdId;
        this.matchScore = matchScore;
        this.resultJson = resultJson;
        this.modelUsed = modelUsed;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.costUsd = costUsd;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getResumeId() {
        return resumeId;
    }

    public Long getJdId() {
        return jdId;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public int getTokensIn() {
        return tokensIn;
    }

    public int getTokensOut() {
        return tokensOut;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
