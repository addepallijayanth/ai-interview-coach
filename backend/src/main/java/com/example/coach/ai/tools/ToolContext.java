package com.example.coach.ai.tools;

import com.example.coach.ai.agent.InterviewHooks;
import com.example.coach.ai.agent.InterviewState;

/**
 * What a tool can see and do during one agent run. Profile and requirements are pre-serialised JSON taken from
 * the stored analysis, so the read-only tools need no database access.
 */
public record ToolContext(InterviewState state, InterviewHooks hooks, String candidateProfileJson,
        String jobRequirementsJson) {}
