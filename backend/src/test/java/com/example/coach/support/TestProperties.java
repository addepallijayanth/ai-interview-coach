package com.example.coach.support;

import com.example.coach.config.CoachProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Application settings for tests that build AI components by hand (no Spring context). */
public final class TestProperties {

    private TestProperties() {}

    public static CoachProperties withBaseUrl(String baseUrl) {
        return new CoachProperties(
                new CoachProperties.App("http://localhost:5173"),
                new CoachProperties.Ai("test-key", baseUrl, Duration.ofSeconds(10), 0, "claude-haiku-5-5",
                        "claude-sonnet-5-5", true, new BigDecimal("5.00"), 30_000, Map.of(
                                "claude-haiku-5-5", price("0.10", "0.50", "0.125", "0.01"),
                                "claude-sonnet-5-5", price("2.00", "10.00", "2.50", "0.10"))),
                new CoachProperties.Interview(10, 20, 30, 4, 4000, Duration.ofMinutes(1)),
                new CoachProperties.Security(null, Duration.ofHours(1), Duration.ofHours(24), Duration.ofHours(1),
                        Duration.ofSeconds(60), List.of()),
                new CoachProperties.Mail("", 587, "", "", true, "AI Interview Coach <no-reply@example.com>"),
                new CoachProperties.RateLimit(150, 10));
    }

    private static CoachProperties.ModelPrice price(String in, String out, String write, String read) {
        return new CoachProperties.ModelPrice(new BigDecimal(in), new BigDecimal(out), new BigDecimal(write),
                new BigDecimal(read));
    }
}
