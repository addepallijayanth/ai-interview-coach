package com.example.coach.ai;

import com.example.coach.config.CoachProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Converts token usage into USD using the per-model prices in application.yml. */
@Component
public class CostCalculator {

    private static final BigDecimal PER_MILLION = BigDecimal.valueOf(1_000_000);

    private final Map<String, CoachProperties.ModelPrice> pricing;

    @Autowired
    public CostCalculator(CoachProperties properties) {
        this(properties.ai().pricing());
    }

    public CostCalculator(Map<String, CoachProperties.ModelPrice> pricing) {
        this.pricing = pricing == null ? Map.of() : pricing;
    }

    public BigDecimal cost(String model, TokenUsage usage) {
        return priceFor(model)
                .map(price -> BigDecimal.valueOf(usage.inputTokens()).multiply(price.input())
                        .add(BigDecimal.valueOf(usage.outputTokens()).multiply(price.output()))
                        .add(BigDecimal.valueOf(usage.cacheWriteTokens()).multiply(price.cacheWrite()))
                        .add(BigDecimal.valueOf(usage.cacheReadTokens()).multiply(price.cacheRead()))
                        .divide(PER_MILLION, 6, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(6));
    }

    /** Exact id first, then the longest configured id that prefixes it (covers suffixed model ids). */
    Optional<CoachProperties.ModelPrice> priceFor(String model) {
        if (model == null) {
            return Optional.empty();
        }
        CoachProperties.ModelPrice exact = pricing.get(model);
        if (exact != null) {
            return Optional.of(exact);
        }
        return pricing.entrySet().stream()
                .filter(entry -> model.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue);
    }
}
