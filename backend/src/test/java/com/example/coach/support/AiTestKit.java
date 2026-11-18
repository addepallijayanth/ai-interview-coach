package com.example.coach.support;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.example.coach.ai.AiCall;
import com.example.coach.ai.ClaudeClient;
import com.example.coach.ai.JsonSchemas;
import com.example.coach.ai.ModelRouter;
import com.example.coach.ai.PromptTemplates;
import com.example.coach.ai.StructuredOutputService;
import com.example.coach.ai.TokenUsage;
import com.example.coach.ai.UsageTracker;
import com.example.coach.ai.agent.InterviewAgent;
import com.example.coach.ai.tools.EndInterviewTool;
import com.example.coach.ai.tools.GetCandidateProfileTool;
import com.example.coach.ai.tools.GetJobRequirementsTool;
import com.example.coach.ai.tools.ScoreAnswerTool;
import com.example.coach.ai.tools.ToolInputParser;
import com.example.coach.ai.tools.ToolRegistry;
import com.example.coach.config.CoachProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The AI layer wired by hand against a stub Claude endpoint: fast tests without a Spring context. */
public final class AiTestKit {

    public final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    public final CoachProperties properties;
    public final List<AiCall> recordedCalls = new CopyOnWriteArrayList<>();
    public final ClaudeClient claude;
    public final ModelRouter router;
    public final JsonSchemas schemas;
    public final PromptTemplates prompts = new PromptTemplates();
    public final StructuredOutputService structured;
    public final ToolRegistry tools;
    public final InterviewAgent agent;

    public AiTestKit(String baseUrl) {
        properties = TestProperties.withBaseUrl(baseUrl);
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey("test-key").baseUrl(baseUrl).maxRetries(0).build();
        claude = new ClaudeClient(client, true, new UsageTracker() {
            @Override
            public void checkBudget(AiCall call) {}

            @Override
            public void record(AiCall call, String model, TokenUsage usage) {
                recordedCalls.add(call);
            }
        });
        router = new ModelRouter(properties);
        schemas = new JsonSchemas(objectMapper);
        structured = new StructuredOutputService(claude, router, schemas, objectMapper, validator);
        ToolInputParser parser = new ToolInputParser(objectMapper, validator);
        tools = new ToolRegistry(List.of(new ScoreAnswerTool(parser), new EndInterviewTool(parser),
                new GetCandidateProfileTool(), new GetJobRequirementsTool()), schemas);
        agent = new InterviewAgent(claude, router, tools, prompts, properties);
    }
}
