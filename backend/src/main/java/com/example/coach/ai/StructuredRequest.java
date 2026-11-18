package com.example.coach.ai;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * One structured-output call: a system prompt, a user prompt, the JSON schema the reply must follow, and optional
 * semantic checks (rules a schema cannot express) plus a repair step used if the retry is still invalid.
 */
public record StructuredRequest<T>(
        AiTask task,
        Long userId,
        Long sessionId,
        String systemPrompt,
        String userPrompt,
        String schemaName,
        Class<T> type,
        Function<T, List<String>> semanticCheck,
        UnaryOperator<T> repair) {

    public static <T> StructuredRequest<T> of(AiTask task, Long userId, String systemPrompt, String userPrompt,
            String schemaName, Class<T> type) {
        return new StructuredRequest<>(task, userId, null, systemPrompt, userPrompt, schemaName, type, null, null);
    }

    public StructuredRequest<T> withChecks(Function<T, List<String>> check, UnaryOperator<T> repairStep) {
        return new StructuredRequest<>(task, userId, sessionId, systemPrompt, userPrompt, schemaName, type, check,
                repairStep);
    }

    public StructuredRequest<T> forSession(Long session) {
        return new StructuredRequest<>(task, userId, session, systemPrompt, userPrompt, schemaName, type,
                semanticCheck, repair);
    }
}
