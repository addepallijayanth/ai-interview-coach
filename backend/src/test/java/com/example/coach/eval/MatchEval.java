package com.example.coach.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Dataset and grading for the match-quality eval. A case passes when the match score lands in its expected range
 * and every must-detect gap appears in missingSkills. "A|B" lists acceptable alternatives for one gap.
 */
public final class MatchEval {

    public record Range(int min, int max) {}

    public record Case(String id, String description, Range expectedScore, List<String> mustDetectGaps,
            String resume, String jobDescription) {}

    public record Result(Case evalCase, Integer score, List<String> missingSkills, List<String> gapsMissed,
            BigDecimal costUsd, long millis, String error) {

        public boolean scoreInRange() {
            return score != null && score >= evalCase.expectedScore().min() && score <= evalCase.expectedScore().max();
        }

        public boolean passed() {
            return error == null && scoreInRange() && gapsMissed.isEmpty();
        }
    }

    private MatchEval() {}

    public static List<Case> loadCases() {
        try (InputStream in = MatchEval.class.getResourceAsStream("/eval/resume-jd-pairs.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Result grade(Case evalCase, int score, List<String> missingSkills, BigDecimal cost, long millis) {
        List<String> missed = evalCase.mustDetectGaps().stream()
                .filter(gap -> !detected(gap, missingSkills))
                .toList();
        return new Result(evalCase, score, missingSkills, missed, cost, millis, null);
    }

    public static Result failed(Case evalCase, String error, long millis) {
        return new Result(evalCase, null, List.of(), evalCase.mustDetectGaps(), BigDecimal.ZERO, millis, error);
    }

    /** A gap counts as detected if any alternative and any missing skill contain one another (case-insensitive). */
    static boolean detected(String gap, List<String> missingSkills) {
        return Arrays.stream(gap.split("\\|")).map(MatchEval::normalize).anyMatch(alternative ->
                missingSkills.stream().map(MatchEval::normalize)
                        .anyMatch(skill -> skill.contains(alternative) || alternative.contains(skill)));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]+", " ").strip();
    }

    public static String markdownReport(List<Result> results) {
        long passed = results.stream().filter(Result::passed).count();
        long inRange = results.stream().filter(Result::scoreInRange).count();
        BigDecimal cost = results.stream().map(Result::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder out = new StringBuilder()
                .append("# Match quality eval\n\n")
                .append("**").append(passed).append("/").append(results.size()).append(" passed** ")
                .append("(score in range: ").append(inRange).append("/").append(results.size())
                .append(", total cost $").append(cost.setScale(4, RoundingMode.HALF_UP)).append(")\n\n")
                .append("| Case | Expected | Score | Gaps missed | Result |\n|---|---|---|---|---|\n");
        for (Result result : results) {
            Case c = result.evalCase();
            out.append("| ").append(c.id())
                    .append(" | ").append(c.expectedScore().min()).append("-").append(c.expectedScore().max())
                    .append(" | ").append(result.score() == null ? "error" : result.score())
                    .append(" | ").append(result.error() != null ? result.error() : String.join(", ", result.gapsMissed()))
                    .append(" | ").append(result.passed() ? "pass" : "FAIL").append(" |\n");
        }
        return out.toString();
    }
}
