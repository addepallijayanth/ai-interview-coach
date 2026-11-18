package com.example.coach.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets (Bucket4j) keyed by user id or client IP. Buckets idle for a day are evicted.
 * For a multi-instance deployment, swap the local buckets for bucket4j's JDBC or Redis proxy manager.
 */
@Component
public class RateLimiter {

    public record Probe(boolean consumed, long remaining, Duration retryAfter) {}

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(25))
            .build();

    public Probe tryConsume(String key, long capacity, Duration period) {
        Bucket bucket = buckets.get(key, k -> newBucket(capacity, period));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return new Probe(probe.isConsumed(), probe.getRemainingTokens(), Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    /** Tokens left without consuming one; a key that was never used has the full capacity. */
    public long remaining(String key, long capacity) {
        Bucket bucket = buckets.getIfPresent(key);
        return bucket == null ? capacity : bucket.getAvailableTokens();
    }

    private static Bucket newBucket(long capacity, Duration period) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, period).build())
                .build();
    }
}
