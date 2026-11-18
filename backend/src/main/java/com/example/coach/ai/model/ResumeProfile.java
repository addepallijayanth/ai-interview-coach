package com.example.coach.ai.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** Structured facts extracted from a resume (schemas/resume-profile.json). */
public record ResumeProfile(
        @NotNull List<@NotBlank String> skills,
        @PositiveOrZero @Max(60) double yearsExperience,
        @NotNull List<@NotBlank String> roles,
        @NotBlank String seniority,
        @NotNull String education,
        @NotNull List<@NotBlank String> highlights) {}
