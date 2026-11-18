package com.example.coach.ai.tools;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/** Content returned to the model as a tool_result block. */
public record ToolResult(String content, boolean error) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message, true);
    }

    /** JSON string literal for {@code value}, escaped with the JSON library rather than by hand. */
    public static String quote(String value) {
        return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(value)) + "\"";
    }
}
