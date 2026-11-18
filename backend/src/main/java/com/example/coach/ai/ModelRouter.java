package com.example.coach.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.example.coach.config.CoachProperties;
import org.springframework.stereotype.Component;

/**
 * Maps each {@link AiTask} to a model: cheap Haiku for extraction and summaries, Sonnet for reasoning and the
 * interviewer. Model ids come from configuration so they can change without a code change.
 */
@Component
public class ModelRouter {

    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    public record ModelChoice(String model, OutputConfig.Effort effort, long maxTokens, boolean serverSideFallback) {}

    private final CoachProperties.Ai ai;

    public ModelRouter(CoachProperties properties) {
        this.ai = properties.ai();
    }

    public ModelChoice route(AiTask task) {
        String model = task.tier() == AiTask.Tier.FAST ? ai.fastModel() : ai.smartModel();
        return new ModelChoice(model, task.effort(), task.maxTokens(),
                ai.refusalFallback() && supportsServerSideFallback(model));
    }

    /**
     * Server-side refusal fallback re-runs a request that a safety classifier declined on another model
     * (for example a security-engineering interview tripping the cyber classifier). Haiku has no fallback.
     */
    static boolean supportsServerSideFallback(String model) {
        return model.startsWith("claude-sonnet-5-5") || model.startsWith("claude-opus-5");
    }

    /** Opts a request into server-side fallback, letting the API pick the fallback model per refusal category. */
    static void applyServerSideFallback(MessageCreateParams.Builder builder) {
        builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA);
        builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }
}
