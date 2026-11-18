package com.example.coach.ai;

import com.anthropic.models.messages.OutputConfig;

/**
 * Every kind of AI call the app makes. The tier decides which model runs it (see {@link ModelRouter}) and the
 * effort level trades thinking depth for cost and latency.
 */
public enum AiTask {
    /** Resume to structured profile: simple extraction, so the cheap model at low effort. */
    EXTRACT_RESUME(Tier.FAST, OutputConfig.Effort.LOW, 8_000),
    /** Job description to structured requirements. */
    EXTRACT_JOB(Tier.FAST, OutputConfig.Effort.LOW, 8_000),
    /** Scoring the match and rewriting bullets needs judgement: the stronger model. */
    MATCH(Tier.SMART, OutputConfig.Effort.MEDIUM, 16_000),
    /** The interviewer agent: multi-step tool use and answer evaluation. */
    INTERVIEW(Tier.SMART, OutputConfig.Effort.MEDIUM, 16_000),
    /** Summarising interview results into a study plan. */
    STUDY_PLAN(Tier.FAST, OutputConfig.Effort.MEDIUM, 8_000);

    public enum Tier { FAST, SMART }

    private final Tier tier;
    private final OutputConfig.Effort effort;
    private final long maxTokens;

    AiTask(Tier tier, OutputConfig.Effort effort, long maxTokens) {
        this.tier = tier;
        this.effort = effort;
        this.maxTokens = maxTokens;
    }

    public Tier tier() {
        return tier;
    }

    public OutputConfig.Effort effort() {
        return effort;
    }

    /** Includes room for adaptive thinking, which counts toward max_tokens. */
    public long maxTokens() {
        return maxTokens;
    }
}
