package com.example.coach.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks contact details before resume text is stored or sent to the AI: emails, phone numbers, social profile
 * URLs and postal addresses. Deliberately conservative (regex heuristics, no NER): it prefers missing an unusual
 * address format over mangling ordinary resume text such as dates and metrics.
 */
@Component
public class PiiRedactor {

    public record Result(String text, Map<String, Integer> counts) {}

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PROFILE_URL = Pattern.compile(
            "(?i)\\b(?:https?://)?(?:www\\.)?(?:linkedin\\.com/(?:in|pub)|github\\.com|gitlab\\.com|twitter\\.com"
                    + "|x\\.com|facebook\\.com|instagram\\.com)/[A-Za-z0-9_.%-]+/?");

    /** "123 Main Street", "221B Baker Street", "5th Cross Road, Apt 4". Words before the suffix must be capitalised. */
    private static final Pattern STREET = Pattern.compile(
            "\\b\\d{1,6}(?:st|nd|rd|th|[A-Za-z])?,?\\s+(?:[A-Z][A-Za-z0-9.'-]*,?\\s+){0,4}"
                    + "(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Way|Place|Pl|Terrace"
                    + "|Circle|Cir|Highway|Hwy|Parkway|Pkwy|Nagar|Marg|Colony|Layout)\\b\\.?"
                    + "(?:,?\\s*(?:Apt|Apartment|Suite|Ste|Unit|Flat|#)\\.?\\s*[A-Za-z0-9-]+)?");

    /** "Austin, TX 78701" */
    private static final Pattern US_CITY_STATE_ZIP =
            Pattern.compile("\\b[A-Z][a-zA-Z]+(?:\\s[A-Z][a-zA-Z]+)?,\\s*[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?\\b");

    /** "Bengaluru - 560001", "Pune, 411 001", "PIN: 560001" */
    private static final Pattern INDIA_PIN = Pattern.compile(
            "\\b(?:[A-Z][a-zA-Z]+\\s*[-,]\\s*\\d{3}\\s?\\d{3}|(?i:pin(?:\\s*code)?)\\s*[:-]?\\s*\\d{3}\\s?\\d{3})\\b");

    /** "SW1A 1AA" */
    private static final Pattern UK_POSTCODE = Pattern.compile("\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s+\\d[A-Z]{2}\\b");

    /** Digit groups that may form a phone number; kept only if they contain 10 to 15 digits in total. */
    private static final Pattern PHONE_CANDIDATE = Pattern.compile(
            "(?<![\\w/.-])(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(\\d{1,5}\\)[\\s.-]?)?\\d{2,15}(?:[\\s.-]\\d{1,5}){0,4}(?![\\w/-])");

    public Result redact(String text) {
        if (text == null || text.isEmpty()) {
            return new Result(text == null ? "" : text, Map.of());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        String out = replaceAll(text, EMAIL, "[EMAIL]", "email", counts);
        out = replaceAll(out, PROFILE_URL, "[PROFILE_URL]", "profileUrl", counts);
        out = replaceAll(out, STREET, "[ADDRESS]", "address", counts);
        out = replaceAll(out, US_CITY_STATE_ZIP, "[ADDRESS]", "address", counts);
        out = replaceAll(out, INDIA_PIN, "[ADDRESS]", "address", counts);
        out = replaceAll(out, UK_POSTCODE, "[ADDRESS]", "address", counts);
        out = replacePhones(out, counts);
        return new Result(out, counts);
    }

    private static String replaceAll(String text, Pattern pattern, String placeholder, String kind,
            Map<String, Integer> counts) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        int found = 0;
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(placeholder));
            found++;
        }
        matcher.appendTail(out);
        if (found > 0) {
            counts.merge(kind, found, Integer::sum);
        }
        return out.toString();
    }

    private static String replacePhones(String text, Map<String, Integer> counts) {
        Matcher matcher = PHONE_CANDIDATE.matcher(text);
        StringBuilder out = new StringBuilder();
        int found = 0;
        while (matcher.find()) {
            String candidate = matcher.group();
            long digits = candidate.chars().filter(Character::isDigit).count();
            if (digits >= 10 && digits <= 15) {
                matcher.appendReplacement(out, "[PHONE]");
                found++;
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(candidate));
            }
        }
        matcher.appendTail(out);
        if (found > 0) {
            counts.merge("phone", found, Integer::sum);
        }
        return out.toString();
    }
}
