package com.example.coach.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs in every build: keeps the eval dataset well-formed and the grading logic honest (no API key needed). */
class MatchEvalGradingTest {

    @Test
    void datasetHasTwentyWellFormedCases() {
        List<MatchEval.Case> cases = MatchEval.loadCases();
        assertThat(cases).hasSize(20);
        assertThat(cases).extracting(MatchEval.Case::id).doesNotHaveDuplicates();
        for (MatchEval.Case c : cases) {
            assertThat(c.expectedScore().min()).as(c.id()).isBetween(0, c.expectedScore().max());
            assertThat(c.expectedScore().max()).as(c.id()).isLessThanOrEqualTo(100);
            assertThat(c.resume()).as(c.id()).hasSizeGreaterThan(150);
            assertThat(c.jobDescription()).as(c.id()).hasSizeGreaterThan(150);
        }
        // The set spans strong, partial and poor matches.
        assertThat(cases).anyMatch(c -> c.expectedScore().min() >= 75);
        assertThat(cases).anyMatch(c -> c.expectedScore().max() <= 25);
    }

    @Test
    void gapsMatchAlternativesAndSubstringsCaseInsensitively() {
        assertThat(MatchEval.detected("Go|Golang", List.of("golang"))).isTrue();
        assertThat(MatchEval.detected("Jetpack Compose|Compose", List.of("Jetpack Compose UI"))).isTrue();
        assertThat(MatchEval.detected("Kubernetes", List.of("Kafka", "Terraform"))).isFalse();
    }

    @Test
    void aCasePassesOnlyWithScoreInRangeAndAllGapsFound() {
        MatchEval.Case c = MatchEval.loadCases().stream().filter(x -> x.id().equals("java-to-go")).findFirst().orElseThrow();
        assertThat(MatchEval.grade(c, 60, List.of("Go", "gRPC", "Kubernetes"), BigDecimal.ONE, 10).passed()).isTrue();
        assertThat(MatchEval.grade(c, 90, List.of("Go", "gRPC"), BigDecimal.ONE, 10).passed()).isFalse();
        MatchEval.Result missedGap = MatchEval.grade(c, 60, List.of("Go"), BigDecimal.ONE, 10);
        assertThat(missedGap.passed()).isFalse();
        assertThat(missedGap.gapsMissed()).containsExactly("gRPC");
        assertThat(MatchEval.markdownReport(List.of(missedGap))).contains("0/1 passed", "| java-to-go | 45-72 | 60 | gRPC | FAIL |");
    }
}
