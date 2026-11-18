package com.example.coach.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** "What to study next" plan generated for the final report (schemas/study-plan.json). */
public record StudyPlan(@NotBlank String summary, @NotEmpty List<@Valid FocusArea> focusAreas) {

    public record FocusArea(
            @NotBlank String topic,
            @NotBlank String priority,
            @NotBlank String why,
            @NotEmpty List<@NotBlank String> actions,
            @NotNull List<@NotBlank String> resources) {}
}
