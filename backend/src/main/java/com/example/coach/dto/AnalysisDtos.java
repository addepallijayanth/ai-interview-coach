package com.example.coach.dto;

import com.example.coach.ai.model.JobRequirements;
import com.example.coach.ai.model.MatchResult;
import com.example.coach.ai.model.ResumeProfile;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class AnalysisDtos {

    private AnalysisDtos() {}

    public record AnalysisRequest(@NotNull Long resumeId, @NotNull Long jdId) {}

    public record AnalysisResponse(
            Long id,
            Long resumeId,
            Long jdId,
            String jobTitle,
            String company,
            int matchScore,
            MatchResult match,
            ResumeProfile candidate,
            JobRequirements job,
            String modelUsed,
            int tokensIn,
            int tokensOut,
            BigDecimal costUsd,
            Instant createdAt) {}

    public record AnalysisSummary(Long id, String jobTitle, String company, int matchScore, Instant createdAt) {}
}
