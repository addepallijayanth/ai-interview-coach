package com.example.coach.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads prompts from {@code classpath:prompts/*.txt} and fills {@code {{variables}}}. Keeping prompts in files
 * makes them reviewable in diffs and keeps the cached prefix byte-identical between requests.
 */
@Component
public class PromptTemplates {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public String get(String name) {
        return templates.computeIfAbsent(name, PromptTemplates::load);
    }

    public String render(String name, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(get(name));
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Prompt '" + name + "' needs variable '" + matcher.group(1) + "'");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Wraps untrusted text (resumes, job descriptions, answers) in XML-style tags so the model can tell data from
     * instructions. Any copy of the same tag inside the text is defused so it cannot close the wrapper early.
     */
    public static String untrusted(String tag, String content) {
        return "<" + tag + ">\n" + sanitize(tag, content) + "\n</" + tag + ">";
    }

    public static String sanitize(String tag, String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("(?i)<\\s*/?\\s*" + Pattern.quote(tag) + "[^>]*>", "[" + tag + "]").strip();
    }

    private static String load(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt template: " + name, e);
        }
    }
}
