package com.example.coach.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class GetJobRequirementsTool implements AgentTool {

    @Override
    public String name() {
        return "get_job_requirements";
    }

    @Override
    public String description() {
        return "Returns the structured requirements of the target job: title, seniority, years required, required "
                + "skills, nice-to-have skills and main responsibilities. Call this once at the start of the "
                + "interview, before asking the first question.";
    }

    @Override
    public String statusMessage() {
        return "Reading the job requirements...";
    }

    @Override
    public ToolResult execute(JsonNode input, ToolContext context) {
        return ToolResult.ok(context.jobRequirementsJson());
    }
}
