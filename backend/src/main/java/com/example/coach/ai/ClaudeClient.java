package com.example.coach.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.Usage;
import com.example.coach.config.CoachProperties;
import com.example.coach.exception.AiException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The single gateway to the Claude API. Every call goes through here so that budget checks, usage accounting,
 * logging and error translation happen in one place.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final AnthropicClient client;
    private final boolean configured;
    private final UsageTracker usageTracker;

    @Autowired
    public ClaudeClient(AnthropicClient client, CoachProperties properties, UsageTracker usageTracker) {
        this(client, properties.ai().configured(), usageTracker);
    }

    public ClaudeClient(AnthropicClient client, boolean configured, UsageTracker usageTracker) {
        this.client = client;
        this.configured = configured;
        this.usageTracker = usageTracker;
    }

    public boolean isConfigured() {
        return configured;
    }

    public Message create(MessageCreateParams params, AiCall call) {
        beforeCall(call);
        long started = System.nanoTime();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (RuntimeException e) {
            throw translate(e);
        }
        afterCall(call, message, started);
        return message;
    }

    /**
     * Streams a response, forwarding text deltas as they arrive, and returns the fully assembled message
     * (tool_use inputs included) once the stream ends.
     */
    public Message stream(MessageCreateParams params, AiCall call, Consumer<String> onTextDelta) {
        beforeCall(call);
        long started = System.nanoTime();
        MessageAccumulator accumulator = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            stream.stream().forEach(event -> {
                accumulator.accumulate(event);
                event.contentBlockDelta()
                        .flatMap(delta -> delta.delta().text())
                        .ifPresent(text -> onTextDelta.accept(text.text()));
            });
        } catch (RuntimeException e) {
            throw translate(e);
        }
        Message message = accumulator.message();
        afterCall(call, message, started);
        return message;
    }

    public static TokenUsage usageOf(Message message) {
        Usage usage = message.usage();
        return new TokenUsage(usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }

    private void beforeCall(AiCall call) {
        if (!configured) {
            throw AiException.notConfigured();
        }
        usageTracker.checkBudget(call);
    }

    private void afterCall(AiCall call, Message message, long startedNanos) {
        TokenUsage usage = usageOf(message);
        String model = message.model().asString();
        usageTracker.record(call, model, usage);
        log.info("claude task={} model={} stop={} in={} out={} cacheRead={} cacheWrite={} ms={}",
                call.task(), model, message.stopReason().map(Object::toString).orElse("?"), usage.inputTokens(),
                usage.outputTokens(), usage.cacheReadTokens(), usage.cacheWriteTokens(),
                (System.nanoTime() - startedNanos) / 1_000_000);
    }

    /** Maps SDK exceptions (most specific first) to messages that are safe to show to users. */
    static RuntimeException translate(RuntimeException e) {
        if (e instanceof AiException) {
            return e;
        }
        if (e instanceof RateLimitException || e instanceof OverloadedException) {
            return new AiException(AiException.Kind.BUSY,
                    "The AI service is busy right now. Please try again in a minute.", e);
        }
        if (e instanceof UnauthorizedException || e instanceof PermissionDeniedException) {
            return new AiException(AiException.Kind.UNAVAILABLE, "The server's AI credentials were rejected.", e);
        }
        if (e instanceof BadRequestException bad) {
            log.error("Claude rejected the request: {}", bad.apiMessage().orElse(bad.getMessage()));
            return new AiException(AiException.Kind.UNAVAILABLE, "The AI request was rejected.", e);
        }
        if (e instanceof AnthropicServiceException service) {
            return new AiException(AiException.Kind.UNAVAILABLE,
                    "The AI service returned an error (HTTP " + service.statusCode() + "). Please try again.", e);
        }
        if (e instanceof AnthropicIoException) {
            return new AiException(AiException.Kind.UNAVAILABLE, "Could not reach the AI service. Please try again.", e);
        }
        if (e instanceof AnthropicException) {
            // Typically malformed streamed tool input (eager input streaming hands validation to the client).
            return new AiException(AiException.Kind.BAD_OUTPUT, "The AI response could not be read.", e);
        }
        return e;
    }
}
