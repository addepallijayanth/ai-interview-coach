package com.example.coach.ai.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Input of the score_answer tool (schemas/tools/score_answer.json). */
public record ScoreAnswerInput(
        @JsonProperty("question_no") @Positive int questionNo,
        @Min(0) @Max(10) int score,
        @NotNull List<@NotBlank String> strengths,
        @NotNull List<@NotBlank String> weaknesses,
        @JsonProperty("model_answer") @NotBlank String modelAnswer) {}
