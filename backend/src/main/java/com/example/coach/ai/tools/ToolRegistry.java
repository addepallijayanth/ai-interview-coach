package com.example.coach.ai.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.example.coach.ai.JsonSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Collects every {@link AgentTool} bean and exposes them to the API. Tool definitions are built once and kept
 * in name order: the tool list renders first in every request, so it must be byte-identical across turns for
 * prompt caching and for replayed conversations to stay valid.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools;
    private final List<ToolUnion> apiTools;

    public ToolRegistry(List<AgentTool> agentTools, JsonSchemas schemas) {
        TreeMap<String, AgentTool> byName = new TreeMap<>();
        agentTools.forEach(tool -> byName.put(tool.name(), tool));
        this.tools = Collections.unmodifiableMap(byName);
        this.apiTools = byName.values().stream()
                .map(tool -> ToolUnion.ofTool(toApiTool(tool, schemas.node("tools/" + tool.name()))))
                .toList();
    }

    public List<ToolUnion> apiTools() {
        return apiTools;
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    /** Exact name first, then a case-insensitive match: a near-miss name is accepted when it is unambiguous. */
    public Optional<AgentTool> find(String name) {
        AgentTool exact = tools.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        return tools.values().stream().filter(tool -> tool.name().equalsIgnoreCase(name)).findFirst();
    }

    public ToolResult execute(String name, JsonNode input, ToolContext context) {
        Optional<AgentTool> tool = find(name);
        if (tool.isEmpty()) {
            return ToolResult.error("Unknown tool '" + name + "'. Available tools: " + String.join(", ", tools.keySet()));
        }
        try {
            return tool.get().execute(input, context);
        } catch (InvalidToolInputException e) {
            return ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Tool {} failed", name, e);
            return ToolResult.error("The tool failed to run. Try again or continue without it.");
        }
    }

    private static Tool toApiTool(AgentTool tool, JsonNode schema) {
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        schema.path("properties").properties()
                .forEach(entry -> properties.putAdditionalProperty(entry.getKey(), JsonValue.fromJsonNode(entry.getValue())));
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(node -> required.add(node.asText()));
        return Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required(required)
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .strict(true)               // schema-valid arguments (forced tool_choice is not available on Sonnet 5.5)
                .eagerInputStreaming(true)  // stream tool input as generated; validated client-side in ToolInputParser
                .build();
    }
}
