package com.example.coach.dto;

import com.example.coach.ai.agent.InterviewLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InterviewDtos {

    private InterviewDtos() {}

    /** {@code level} is optional (STANDARD); question counts per level come from coach.interview.*. */
    public record StartInterviewRequest(@NotNull Long analysisId, InterviewLevel level) {}

    public record AnswerRequest(@NotBlank @Size(max = 4000) String answer) {}

    public record AnswerFeedback(List<String> strengths, List<String> weaknesses, String modelAnswer) {}

    /** {@code category} is the enum name (e.g. SYSTEM_DESIGN), {@code categoryLabel} its display name. */
    public record TurnResponse(int questionNo, String category, String categoryLabel, String question, String answer,
            Integer score, AnswerFeedback feedback) {}

    public record InterviewResponse(
            Long id,
            Long analysisId,
            String status,
            String level,
            String levelLabel,
            int questionTarget,
            int questionsAnswered,
            BigDecimal overallScore,
            String summary,
            String jobTitle,
            String company,
            /** An answer was submitted and the interviewer has not scored it yet: open the stream. */
            boolean awaitingFeedback,
            List<TurnResponse> turns,
            Instant createdAt,
            Instant endedAt) {}

    public record InterviewSummary(Long id, Long analysisId, String status, String level, String levelLabel,
            String jobTitle, int questionTarget, int questionsAnswered, BigDecimal overallScore, Instant createdAt) {}

    // ---- Server-sent events on GET /api/interviews/{id}/stream ----

    /** event: status */
    public record StatusEvent(String message) {}

    /** event: token (draft text of the next question; replaced by the question event) */
    public record TokenEvent(String text) {}

    /** event: feedback */
    public record FeedbackEvent(int questionNo, int score, List<String> strengths, List<String> weaknesses,
            String modelAnswer) {}

    /** event: question */
    public record QuestionEvent(int questionNo, String question, int questionTarget, String category,
            String categoryLabel) {}

    /** event: ended */
    public record EndedEvent(BigDecimal overallScore, String summary, int questionsAnswered) {}

    /** event: error */
    public record ErrorEvent(String code, String message, boolean retryable) {}
}
