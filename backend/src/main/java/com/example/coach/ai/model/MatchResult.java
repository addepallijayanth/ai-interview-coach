package com.example.coach.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Resume vs job comparison (schemas/match-result.json). */
public record MatchResult(
        @Min(0) @Max(100) int matchScore,
        @NotNull List<@NotBlank String> matchedSkills,
        @NotNull List<@NotBlank String> missingSkills,
        @NotNull List<@NotBlank String> strengths,
        @NotNull List<@Valid Suggestion> suggestions,
        @NotBlank String summary) {

    /** A rewrite of one resume bullet. {@code original} must appear verbatim in the resume. */
    public record Suggestion(@NotBlank String original, @NotBlank String improved, @NotBlank String reason) {}
}
