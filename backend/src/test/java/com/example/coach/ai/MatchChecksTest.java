package com.example.coach.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coach.ai.model.MatchResult;
import com.example.coach.ai.model.MatchResult.Suggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchChecksTest {

    private static final String RESUME = """
            • Built Spring Boot microservices processing 2 million payments per day.
            - Migrated a monolith to Kubernetes, cutting deployment time by 60%
            """;

    @Test
    void acceptsBulletsQuotedWithDifferentWhitespaceBulletsOrQuotes() {
        MatchResult result = match(List.of(
                suggestion("Built Spring Boot  microservices processing 2 million payments per day"),
                suggestion("migrated a monolith to Kubernetes, cutting deployment time by 60%.")), List.of("Kafka"));
        assertThat(MatchChecks.problems(result, RESUME)).isEmpty();
    }

    @Test
    void flagsInventedOriginalsAndContradictorySkills() {
        MatchResult result = match(List.of(suggestion("Led a team of 20 engineers at Google")), List.of("Java", "Kafka"));
        assertThat(MatchChecks.problems(result, RESUME))
                .anySatisfy(problem -> assertThat(problem).contains("suggestions[0].original"))
                .anySatisfy(problem -> assertThat(problem).contains("\"Java\" is listed in both"));
    }

    @Test
    void repairKeepsOnlyVerifiableContent() {
        MatchResult result = match(List.of(
                suggestion("Built Spring Boot microservices processing 2 million payments per day"),
                suggestion("Invented bullet")), List.of("Java", "Kafka"));
        MatchResult repaired = MatchChecks.repair(result, RESUME);
        assertThat(repaired.suggestions()).hasSize(1);
        assertThat(repaired.missingSkills()).containsExactly("Kafka");
        assertThat(MatchChecks.problems(repaired, RESUME)).isEmpty();
    }

    private static MatchResult match(List<Suggestion> suggestions, List<String> missing) {
        return new MatchResult(70, List.of("Java", "Spring Boot"), missing, List.of("Payments"), suggestions, "Summary");
    }

    private static Suggestion suggestion(String original) {
        return new Suggestion(original, "Improved bullet", "Reason");
    }
}
