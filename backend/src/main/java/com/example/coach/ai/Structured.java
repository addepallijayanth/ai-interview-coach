package com.example.coach.ai;

/** A validated structured result plus what it cost to produce. */
public record Structured<T>(T value, String model, TokenUsage usage, int attempts) {

    /** A value reused from the database cache: no model call was made. */
    public static <T> Structured<T> cached(T value) {
        return new Structured<>(value, null, TokenUsage.ZERO, 0);
    }

    public boolean fromCache() {
        return attempts == 0;
    }
}
