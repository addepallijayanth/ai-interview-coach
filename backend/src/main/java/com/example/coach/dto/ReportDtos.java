package com.example.coach.dto;

import com.example.coach.ai.model.StudyPlan;
import com.example.coach.dto.InterviewDtos.TurnResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    /** {@code studyPlan} is null when it could not be generated (for example the AI was unavailable). */
    public record ReportResponse(
            Long sessionId,
            String jobTitle,
            String company,
            String level,
            String levelLabel,
            int matchScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            BigDecimal overallScore,
            int questionsAnswered,
            int questionTarget,
            String summary,
            List<CategoryScore> categoryScores,
            List<TurnResponse> turns,
            StudyPlan studyPlan,
            BigDecimal aiCostUsd,
            Instant endedAt) {}

    /** Average score of the scored answers in one question category; null when none was scored. */
    public record CategoryScore(String category, String label, int asked, int scored, BigDecimal averageScore) {}

    public record UsageResponse(long calls, long inputTokens, long outputTokens, long cacheReadTokens,
            BigDecimal costUsd, long aiRequestsRemainingToday, int aiRequestsPerDay) {}

    /** Public settings for the web app. {@code emailEnabled} is false when links are only written to the log. */
    public record MetaResponse(boolean aiConfigured, String fastModel, String smartModel, boolean emailEnabled,
            List<LevelInfo> interviewLevels, List<CategoryInfo> questionCategories, int maxUploadMb,
            int maxAnswerChars) {}

    /** An interview level and how many questions of each category it asks. */
    public record LevelInfo(String id, String label, String description, int questions,
            List<CategoryCount> breakdown) {}

    public record CategoryCount(String category, String label, int count) {}

    public record CategoryInfo(String id, String label, String description) {}
}
