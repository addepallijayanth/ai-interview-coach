package com.example.coach.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds Claude Messages API responses for WireMock: plain JSON messages and server-sent-event streams in the
 * exact wire format the API uses, so tests exercise the real SDK parsing (including the stream accumulator).
 */
public final class AnthropicStubs {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger IDS = new AtomicInteger();

    private AnthropicStubs() {}

    /** One content block of a stubbed response. */
    public sealed interface Block permits Text, ToolUse, Thinking {}

    public record Text(String text) implements Block {}

    public record ToolUse(String id, String name, String inputJson) implements Block {}

    /** A thinking block as returned with the default display ("omitted"): empty text plus a signature. */
    public record Thinking(String signature) implements Block {}

    public static Block text(String text) {
        return new Text(text);
    }

    public static Block toolUse(String name, String inputJson) {
        return new ToolUse("toolu_" + IDS.incrementAndGet(), name, inputJson);
    }

    public static Block thinking(String signature) {
        return new Thinking(signature);
    }

    /** Non-streaming response whose only text block is {@code text} (e.g. structured-output JSON). */
    public static ResponseDefinitionBuilder message(String model, String text) {
        return message(model, "end_turn", text);
    }

    public static ResponseDefinitionBuilder message(String model, String stopReason, String text) {
        ObjectNode message = baseMessage(model, stopReason);
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "thinking").put("thinking", "").put("signature", "sig-" + IDS.incrementAndGet());
        content.addObject().put("type", "text").put("text", text);
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(message.toString());
    }

    /** A safety-classifier decline: HTTP 200, stop_reason "refusal", no content. */
    public static ResponseDefinitionBuilder refusal(String model) {
        ObjectNode message = baseMessage(model, "refusal");
        message.putArray("content");
        message.putObject("stop_details").put("type", "refusal").put("category", "cyber");
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(message.toString());
    }

    /** Streaming (SSE) response made of the given blocks. */
    public static ResponseDefinitionBuilder stream(String model, String stopReason, List<Block> blocks) {
        StringBuilder sse = new StringBuilder();
        ObjectNode start = JSON.createObjectNode().put("type", "message_start");
        ObjectNode message = baseMessage(model, null);
        message.putArray("content");
        message.putNull("stop_reason");
        start.set("message", message);
        event(sse, "message_start", start);

        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            ObjectNode blockStart = JSON.createObjectNode().put("type", "content_block_start").put("index", index);
            ObjectNode delta = JSON.createObjectNode().put("type", "content_block_delta").put("index", index);
            switch (block) {
                case Text text -> {
                    blockStart.putObject("content_block").put("type", "text").put("text", "");
                    event(sse, "content_block_start", blockStart);
                    // Split the text into two deltas to exercise token streaming.
                    int middle = text.text().length() / 2;
                    for (String part : List.of(text.text().substring(0, middle), text.text().substring(middle))) {
                        ObjectNode textDelta = delta.deepCopy();
                        textDelta.putObject("delta").put("type", "text_delta").put("text", part);
                        event(sse, "content_block_delta", textDelta);
                    }
                }
                case ToolUse use -> {
                    ObjectNode contentBlock = blockStart.putObject("content_block");
                    contentBlock.put("type", "tool_use").put("id", use.id()).put("name", use.name());
                    contentBlock.putObject("input");
                    event(sse, "content_block_start", blockStart);
                    delta.putObject("delta").put("type", "input_json_delta").put("partial_json", use.inputJson());
                    event(sse, "content_block_delta", delta);
                }
                case Thinking thinking -> {
                    blockStart.putObject("content_block").put("type", "thinking").put("thinking", "").put("signature", "");
                    event(sse, "content_block_start", blockStart);
                    delta.putObject("delta").put("type", "signature_delta").put("signature", thinking.signature());
                    event(sse, "content_block_delta", delta);
                }
            }
            event(sse, "content_block_stop", JSON.createObjectNode().put("type", "content_block_stop").put("index", index));
        }

        ObjectNode messageDelta = JSON.createObjectNode().put("type", "message_delta");
        messageDelta.putObject("delta").put("stop_reason", stopReason).putNull("stop_sequence");
        messageDelta.putObject("usage").put("input_tokens", 1200).put("output_tokens", 80)
                .put("cache_creation_input_tokens", 0).put("cache_read_input_tokens", 900);
        event(sse, "message_delta", messageDelta);
        event(sse, "message_stop", JSON.createObjectNode().put("type", "message_stop"));
        return aResponse().withStatus(200).withHeader("Content-Type", "text/event-stream").withBody(sse.toString());
    }

    private static ObjectNode baseMessage(String model, String stopReason) {
        ObjectNode message = JSON.createObjectNode();
        message.put("id", "msg_" + IDS.incrementAndGet()).put("type", "message").put("role", "assistant").put("model", model);
        if (stopReason != null) {
            message.put("stop_reason", stopReason);
        }
        message.putNull("stop_sequence");
        message.putObject("usage").put("input_tokens", 1200).put("output_tokens", 300)
                .put("cache_creation_input_tokens", 0).put("cache_read_input_tokens", 0);
        return message;
    }

    private static void event(StringBuilder sse, String name, ObjectNode data) {
        sse.append("event: ").append(name).append('\n').append("data: ").append(data).append("\n\n");
    }
}
