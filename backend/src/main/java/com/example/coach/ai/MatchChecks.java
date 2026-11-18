package com.example.coach.ai;

import com.example.coach.ai.model.MatchResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rules for a {@link MatchResult} that JSON Schema cannot express. The important one: every rewrite suggestion
 * must quote a bullet that really exists in the resume, which catches hallucinated "original" text.
 */
final class MatchChecks {

    private MatchChecks() {}

    static List<String> problems(MatchResult result, String resumeText) {
        List<String> problems = new ArrayList<>();
        String resume = normalize(resumeText);
        for (int i = 0; i < result.suggestions().size(); i++) {
            String original = result.suggestions().get(i).original();
            if (!resume.contains(normalize(original))) {
                problems.add("suggestions[" + i + "].original must be copied exactly from the resume, but \""
                        + abbreviate(original) + "\" does not appear in it");
            }
        }
        Set<String> matched = result.matchedSkills().stream().map(MatchChecks::normalize).collect(Collectors.toSet());
        for (String missing : result.missingSkills()) {
            if (matched.contains(normalize(missing))) {
                problems.add("\"" + missing + "\" is listed in both matchedSkills and missingSkills");
            }
        }
        return problems;
    }

    /** Keeps what is verifiably correct: drops non-verbatim suggestions and contradictory missing skills. */
    static MatchResult repair(MatchResult result, String resumeText) {
        String resume = normalize(resumeText);
        Set<String> matched = result.matchedSkills().stream().map(MatchChecks::normalize).collect(Collectors.toSet());
        return new MatchResult(
                Math.clamp(result.matchScore(), 0, 100),
                result.matchedSkills(),
                result.missingSkills().stream().filter(skill -> !matched.contains(normalize(skill))).toList(),
                result.strengths(),
                result.suggestions().stream()
                        .filter(suggestion -> resume.contains(normalize(suggestion.original())))
                        .toList(),
                result.summary());
    }

    /** Case, whitespace, bullet and typographic-quote insensitive form used for "appears in the resume" checks. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[‘’‛′`]", "'")
                .replaceAll("[“”‟″]", "\"")
                .replaceAll("[‐-―−]", "-")
                .replaceAll("[•●▪◦■□➢►✓✔·*]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip()
                .replaceAll("^-\\s*", "")
                .replaceAll("[.;,:]+$", "");
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
