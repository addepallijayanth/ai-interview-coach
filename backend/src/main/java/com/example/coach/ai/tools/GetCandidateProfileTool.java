package com.example.coach.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class GetCandidateProfileTool implements AgentTool {

    @Override
    public String name() {
        return "get_candidate_profile";
    }

    @Override
    public String description() {
        return "Returns the analysed candidate profile: skills, years of experience, roles, seniority, the "
                + "resume-vs-job match score, the job skills the candidate matched, and their skill gaps. Call this "
                + "once at the start of the interview, before asking the first question.";
    }

    @Override
    public String statusMessage() {
        return "Reviewing your profile...";
    }

    @Override
    public ToolResult execute(JsonNode input, ToolContext context) {
        return ToolResult.ok(context.candidateProfileJson());
    }
}
