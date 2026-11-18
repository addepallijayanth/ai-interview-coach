package com.example.coach.ai;

/**
 * Token counts for one or more calls. {@code inputTokens} excludes cached tokens, which are reported separately
 * (cache reads are billed at a fraction of the input price, cache writes at a premium).
 */
public record TokenUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens,
                cacheReadTokens + other.cacheReadTokens, cacheWriteTokens + other.cacheWriteTokens);
    }

    /** Every prompt token, whether it was billed fresh, read from cache or written to cache. */
    public long totalInputTokens() {
        return inputTokens + cacheReadTokens + cacheWriteTokens;
    }
}
