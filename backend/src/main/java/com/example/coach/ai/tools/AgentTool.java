package com.example.coach.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A Java method the interviewer agent can call. The input schema lives in {@code schemas/tools/<name>.json};
 * Claude decides when to call the tool and the backend executes it.
 */
public interface AgentTool {

    String name();

    /** Tells the model what the tool does and, just as important, when to call it. */
    String description();

    /** Progress line shown to the candidate while the tool runs. */
    String statusMessage();

    /**
     * Runs the tool. Throw {@link InvalidToolInputException} for bad input: the model receives the message as an
     * error result and usually corrects the call on its next turn.
     */
    ToolResult execute(JsonNode input, ToolContext context);
}
