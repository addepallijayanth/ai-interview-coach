package com.example.coach.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Turns raw tool input into a validated record. Tools use eager input streaming, so the API does not buffer and
 * validate their input: the client must check it before running anything.
 */
@Component
public class ToolInputParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ToolInputParser(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> T parse(JsonNode input, Class<T> type) {
        if (input == null || !input.isObject()) {
            throw new InvalidToolInputException("Input must be a JSON object.");
        }
        T value;
        try {
            value = objectMapper.treeToValue(input, type);
        } catch (JsonProcessingException e) {
            throw new InvalidToolInputException("Input does not match the schema: " + e.getOriginalMessage());
        }
        String problems = validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        if (!problems.isEmpty()) {
            throw new InvalidToolInputException("Invalid input: " + problems + ".");
        }
        return value;
    }
}
