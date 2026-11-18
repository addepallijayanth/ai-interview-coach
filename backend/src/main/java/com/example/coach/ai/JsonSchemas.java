package com.example.coach.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * JSON Schemas from {@code classpath:schemas/}. They define the structured-output contract sent to the API and
 * the tool input schemas. Jackson keeps key order, so every request serialises the schema identically
 * (important for prompt caching and for replaying an agent conversation).
 */
@Component
public class JsonSchemas {

    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> cache = new ConcurrentHashMap<>();

    public JsonSchemas(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode node(String name) {
        return cache.computeIfAbsent(name, this::load);
    }

    /** {@code output_config.format} for a schema: the model's reply is constrained to valid JSON of that shape. */
    public JsonOutputFormat outputFormat(String name) {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        node(name).properties().forEach(entry ->
                schema.putAdditionalProperty(entry.getKey(), JsonValue.fromJsonNode(entry.getValue())));
        return JsonOutputFormat.builder().schema(schema.build()).build();
    }

    private JsonNode load(String name) {
        ClassPathResource resource = new ClassPathResource("schemas/" + name + ".json");
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing JSON schema: " + name, e);
        }
    }
}
