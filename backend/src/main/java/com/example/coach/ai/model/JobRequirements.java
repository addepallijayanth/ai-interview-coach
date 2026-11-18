package com.example.coach.ai.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** Structured hiring requirements extracted from a job description (schemas/job-requirements.json). */
public record JobRequirements(
        @NotBlank String title,
        @NotNull String company,
        @NotBlank String seniority,
        @PositiveOrZero @Max(40) double yearsRequired,
        @NotNull List<@NotBlank String> requiredSkills,
        @NotNull List<@NotBlank String> niceToHaveSkills,
        @NotNull List<@NotBlank String> responsibilities) {}
