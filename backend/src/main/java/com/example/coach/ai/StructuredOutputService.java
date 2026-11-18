package com.example.coach.ai;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.example.coach.exception.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gets typed JSON out of Claude. The request uses structured outputs ({@code output_config.format}) so the reply
 * is constrained to the schema; the result is then parsed and checked in Java (Bean Validation plus semantic
 * rules the schema cannot express). An invalid result is retried once with the problems fed back to the model.
 */
@Component
public class StructuredOutputService {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final ClaudeClient claude;
    private final ModelRouter router;
    private final JsonSchemas schemas;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public StructuredOutputService(ClaudeClient claude, ModelRouter router, JsonSchemas schemas,
            ObjectMapper objectMapper, Validator validator) {
        this.claude = claude;
        this.router = router;
        this.schemas = schemas;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> Structured<T> generate(StructuredRequest<T> request) {
        ModelRouter.ModelChoice choice = router.route(request.task());
        AiCall call = new AiCall(request.task(), request.userId(), request.sessionId());
        TokenUsage usage = TokenUsage.ZERO;
        String model = choice.model();
        T lastValue = null;
        List<String> problems = List.of();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String userPrompt = attempt == 1 ? request.userPrompt() : withFeedback(request.userPrompt(), problems);
            Message message = claude.create(buildParams(choice, request, userPrompt), call);
            usage = usage.plus(ClaudeClient.usageOf(message));
            model = message.model().asString();

            StopReason stop = message.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stop)) {
                throw AiException.refused();
            }
            if (StopReason.MAX_TOKENS.equals(stop)) {
                lastValue = null;
                problems = List.of("The answer was cut off before it was complete. Be more concise.");
            } else {
                try {
                    lastValue = objectMapper.readValue(textOf(message), request.type());
                    problems = validate(lastValue, request);
                } catch (JsonProcessingException e) {
                    lastValue = null;
                    problems = List.of("The output was not valid JSON for the schema: " + e.getOriginalMessage());
                }
            }
            if (problems.isEmpty()) {
                return new Structured<>(lastValue, model, usage, attempt);
            }
            log.warn("{} output failed validation on attempt {}: {}", request.task(), attempt, problems);
        }

        if (lastValue != null && request.repair() != null) {
            T repaired = request.repair().apply(lastValue);
            if (validate(repaired, request).isEmpty()) {
                log.info("{} output repaired after retry", request.task());
                return new Structured<>(repaired, model, usage, MAX_ATTEMPTS);
            }
        }
        throw AiException.badOutput(String.join("; ", problems));
    }

    private MessageCreateParams buildParams(ModelRouter.ModelChoice choice, StructuredRequest<?> request,
            String userPrompt) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(choice.model())
                .maxTokens(choice.maxTokens())
                // The instructions are identical for every user, so they are cached and shared across requests.
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(request.systemPrompt())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(userPrompt)
                .outputConfig(OutputConfig.builder()
                        .effort(choice.effort())
                        .format(schemas.outputFormat(request.schemaName()))
                        .build());
        if (choice.serverSideFallback()) {
            ModelRouter.applyServerSideFallback(builder);
        }
        return builder.build();
    }

    private <T> List<String> validate(T value, StructuredRequest<T> request) {
        List<String> problems = validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        if (request.semanticCheck() != null) {
            problems.addAll(request.semanticCheck().apply(value));
        }
        return problems;
    }

    /** Responses can begin with (empty) thinking blocks, so read text blocks by type, never by position. */
    public static String textOf(Message message) {
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
    }

    private static String withFeedback(String userPrompt, List<String> problems) {
        return userPrompt + "\n\n<validation_feedback>\nA previous answer to this request was rejected because:\n"
                + problems.stream().map(problem -> "- " + problem).collect(Collectors.joining("\n"))
                + "\nReturn a corrected answer that fixes every issue.\n</validation_feedback>";
    }
}
