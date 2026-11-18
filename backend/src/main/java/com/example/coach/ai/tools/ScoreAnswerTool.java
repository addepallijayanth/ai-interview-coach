package com.example.coach.ai.tools;

import com.example.coach.ai.agent.InterviewState;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ScoreAnswerTool implements AgentTool {

    private final ToolInputParser parser;

    public ScoreAnswerTool(ToolInputParser parser) {
        this.parser = parser;
    }

    @Override
    public String name() {
        return "score_answer";
    }

    @Override
    public String description() {
        return "Records your 0-10 score and feedback (strengths, weaknesses and a model answer) for the candidate's "
                + "latest answer, and shows it to them. Call this after every candidate answer, before asking the "
                + "next question. The result says how many questions remain, the category of the next question, "
                + "and when the interview is complete.";
    }

    @Override
    public String statusMessage() {
        return "Scoring your answer...";
    }

    @Override
    public ToolResult execute(JsonNode input, ToolContext context) {
        ScoreAnswerInput score = parser.parse(input, ScoreAnswerInput.class);
        if (score.strengths().isEmpty() && score.weaknesses().isEmpty()) {
            throw new InvalidToolInputException("Give at least one strength or weakness.");
        }
        InterviewState state = context.state();
        Integer expected = state.awaitingScore();
        if (expected == null) {
            return ToolResult.error("There is no unscored answer. Ask the next question or call end_interview.");
        }
        if (score.questionNo() != expected) {
            return ToolResult.error("The answer waiting for a score is for question " + expected
                    + ". Call score_answer again with question_no " + expected + ".");
        }

        context.hooks().scored(score);
        state.scoreRecorded();

        int asked = state.questionsAsked();
        int target = state.questionTarget();
        if (state.endRequested()) {
            return ToolResult.ok("Score saved. The candidate has ended the interview: call end_interview now.");
        }
        if (state.complete()) {
            return ToolResult.ok("Score saved for question " + expected + ". All " + target
                    + " questions are done, so the interview is complete: call end_interview now with your summary.");
        }
        String nextStep = score.score() >= 8 ? "They answered strongly, so make the next question harder."
                : score.score() <= 4 ? "They struggled, so keep the next question approachable and supportive."
                : "Keep the difficulty steady.";
        return ToolResult.ok("Score saved for question " + expected + " (" + score.score() + "/10). "
                + (target - asked) + " of " + target + " questions remain. Ask question " + (asked + 1)
                + " now (category: " + state.nextCategory().label() + "). " + nextStep);
    }
}
