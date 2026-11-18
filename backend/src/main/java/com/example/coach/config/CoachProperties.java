package com.example.coach.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All application settings under the {@code coach.*} prefix. Values live in application.yml and can be
 * overridden with environment variables (see .env.example).
 */
@Validated
@ConfigurationProperties(prefix = "coach")
public record CoachProperties(
        @Valid @NotNull App app,
        @Valid @NotNull Ai ai,
        @Valid @NotNull Interview interview,
        @Valid @NotNull Security security,
        @Valid @NotNull Mail mail,
        @Valid @NotNull RateLimit rateLimit) {

    /** {@code baseUrl}: public address of the web app, used for links in emails. */
    public record App(@NotBlank String baseUrl) {

        /** An absolute link to a page of the web app, e.g. {@code link("/verify-email?token=...")}. */
        public String link(String path) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + path;
        }
    }

    public record Ai(
            String apiKey,
            @NotBlank String baseUrl,
            @NotNull Duration timeout,
            @Min(0) int maxRetries,
            /** Cheap, fast model used for extraction and summarisation. */
            @NotBlank String fastModel,
            /** Stronger model used for reasoning, rewriting and the interviewer agent. */
            @NotBlank String smartModel,
            /** Retry policy declines on another model (server-side fallback, Sonnet only). */
            boolean refusalFallback,
            /** Hard stop for the whole deployment, in USD per UTC day. */
            @NotNull BigDecimal dailySpendCapUsd,
            /** Resume / job description text longer than this is truncated before it reaches the model. */
            @Min(1000) int maxDocumentChars,
            /** USD per million tokens, keyed by model id. */
            @NotNull Map<String, ModelPrice> pricing) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record ModelPrice(
            @NotNull BigDecimal input,
            @NotNull BigDecimal output,
            @NotNull BigDecimal cacheWrite,
            @NotNull BigDecimal cacheRead) {}

    public record Interview(
            /** Number of questions in an Easy, Standard and Professional interview. */
            @Min(1) @Max(40) int easyQuestions,
            @Min(1) @Max(40) int standardQuestions,
            @Min(1) @Max(40) int professionalQuestions,
            /** Model calls allowed per agent run (one candidate answer). Stops runaway tool loops. */
            @Min(2) int maxAgentSteps,
            @Min(100) int maxAnswerChars,
            @NotNull Duration streamTimeout) {}

    public record Security(
            String jwtSecret,
            @NotNull Duration jwtTtl,
            @NotNull Duration emailVerificationTtl,
            @NotNull Duration passwordResetTtl,
            /** Minimum gap between two emails of the same kind to one account. */
            @NotNull Duration emailResendCooldown,
            List<String> corsAllowedOrigins) {}

    /** SMTP settings. With a blank {@code host} nothing is sent and email links are logged instead. */
    public record Mail(
            String host,
            @Min(1) @Max(65535) int port,
            String username,
            String password,
            boolean starttls,
            @NotBlank String from) {

        public boolean enabled() {
            return host != null && !host.isBlank();
        }
    }

    public record RateLimit(@Min(1) int aiRequestsPerDay, @Min(1) int authRequestsPerMinute) {}
}
