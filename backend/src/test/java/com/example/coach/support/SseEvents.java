package com.example.coach.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Minimal parser for a complete text/event-stream body. */
public final class SseEvents {

    public record Event(String name, JsonNode data) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private SseEvents() {}

    public static List<Event> parse(String body) {
        List<Event> events = new ArrayList<>();
        for (String chunk : body.replace("\r\n", "\n").split("\n\n")) {
            String name = "message";
            StringBuilder data = new StringBuilder();
            for (String line : chunk.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                }
            }
            if (!data.isEmpty()) {
                try {
                    events.add(new Event(name, JSON.readTree(data.toString())));
                } catch (Exception e) {
                    throw new IllegalStateException("Bad SSE data: " + data, e);
                }
            }
        }
        return events;
    }

    public static List<String> names(List<Event> events) {
        return events.stream().map(Event::name).toList();
    }

    public static Optional<Event> first(List<Event> events, String name) {
        return events.stream().filter(event -> event.name().equals(name)).findFirst();
    }
}
