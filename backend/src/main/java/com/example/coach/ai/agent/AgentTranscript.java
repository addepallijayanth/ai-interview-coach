package com.example.coach.ai.agent;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * The interviewer's conversation history, exactly as the API produced it (tool calls, tool results and thinking
 * blocks included). It is persisted between HTTP requests and only ever appended to: newer Claude models verify
 * that earlier turns are unchanged before they accept replayed thinking blocks, and an unchanged prefix is also
 * what makes prompt caching work.
 */
public final class AgentTranscript {

    private static final JsonMapper MAPPER = ObjectMappers.jsonMapper();
    private static final TypeReference<List<MessageParam>> MESSAGE_LIST = new TypeReference<>() {};

    private final List<MessageParam> messages;

    private AgentTranscript(List<MessageParam> messages) {
        this.messages = messages;
    }

    public static AgentTranscript empty() {
        return new AgentTranscript(new ArrayList<>());
    }

    public static AgentTranscript fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            return new AgentTranscript(new ArrayList<>(MAPPER.readValue(json, MESSAGE_LIST)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Interview transcript could not be read", e);
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Interview transcript could not be written", e);
        }
    }

    public List<MessageParam> messages() {
        return List.copyOf(messages);
    }

    public void add(MessageParam message) {
        messages.add(message);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int size() {
        return messages.size();
    }

    /** True when the history ends with a user or system turn, i.e. it is the model's move. */
    public boolean awaitingModel() {
        if (messages.isEmpty()) {
            return false;
        }
        MessageParam last = messages.getLast();
        return last.isUser() || last.isSystem();
    }

    public boolean endsWithSystemMessage() {
        return !messages.isEmpty() && messages.getLast().isSystem();
    }
}
