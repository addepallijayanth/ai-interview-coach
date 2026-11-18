package com.example.coach.ai.tools;

import com.example.coach.ai.agent.InterviewState;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

@Component
public class EndInterviewTool implements AgentTool {

    /** Input of the end_interview tool (schemas/tools/end_interview.json). */
    public record EndInterviewInput(@NotBlank String summary) {}

    private final ToolInputParser parser;

    public EndInterviewTool(ToolInputParser parser) {
        this.parser = parser;
    }

    @Override
    public String name() {
        return "end_interview";
    }

    @Override
    public String description() {
        return "Ends the interview and records your final summary for the candidate. Call this when score_answer "
                + "reports that the interview is complete, or when the candidate ends the interview early. Never "
                + "ask another question after calling it.";
    }

    @Override
    public String statusMessage() {
        return "Wrapping up the interview...";
    }

    @Override
    public ToolResult execute(JsonNode input, ToolContext context) {
        EndInterviewInput end = parser.parse(input, EndInterviewInput.class);
        InterviewState state = context.state();
        if (state.awaitingScore() != null) {
            return ToolResult.error("Call score_answer for question " + state.awaitingScore() + " before ending.");
        }
        if (!state.complete() && !state.endRequested()) {
            return ToolResult.error("The interview is not finished: " + state.questionsAsked() + " of "
                    + state.questionTarget() + " questions asked. Ask the next question instead.");
        }
        context.hooks().ended(end.summary().strip());
        state.markEnded();
        return ToolResult.ok("The interview has ended. Do not reply further.");
    }
}
