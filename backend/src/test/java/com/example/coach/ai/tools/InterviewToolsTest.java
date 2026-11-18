package com.example.coach.ai.tools;

import static com.example.coach.ai.agent.QuestionCategory.BEHAVIOURAL;
import static com.example.coach.ai.agent.QuestionCategory.HR;
import static com.example.coach.ai.agent.QuestionCategory.SYSTEM_DESIGN;
import static com.example.coach.ai.agent.QuestionCategory.TECHNICAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.ObjectMappers;
import com.example.coach.ai.agent.AgentTranscript;
import com.example.coach.ai.agent.InterviewHooks;
import com.example.coach.ai.agent.InterviewState;
import com.example.coach.ai.agent.QuestionCategory;
import com.example.coach.support.AiTestKit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewToolsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AiTestKit kit = new AiTestKit("http://localhost:1");
    private final List<ScoreAnswerInput> scores = new ArrayList<>();

    private static final List<QuestionCategory> PLAN = List.of(HR, SYSTEM_DESIGN, BEHAVIOURAL);

    @Test
    void scoreAnswerRecordsFeedbackAndTellsTheModelWhatIsNext() throws Exception {
        InterviewState state = new InterviewState(PLAN, 1, 1, false);
        ToolResult result = kit.tools.execute("score_answer", score(1, 8), context(state));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("2 of 3 questions remain", "Ask question 2 now (category: System design)",
                "harder");
        assertThat(scores).hasSize(1);
        assertThat(state.awaitingScore()).isNull();
    }

    @Test
    void scoreAnswerOnTheLastQuestionSaysToEnd() throws Exception {
        ToolResult result = kit.tools.execute("score_answer", score(2, 5),
                context(new InterviewState(List.of(HR, TECHNICAL), 2, 2, false)));
        assertThat(result.content()).contains("interview is complete", "call end_interview");
    }

    @Test
    void scoreAnswerRejectsTheWrongQuestionAndOutOfRangeScores() throws Exception {
        InterviewState state = new InterviewState(PLAN, 2, 2, false);
        ToolResult wrongQuestion = kit.tools.execute("score_answer", score(1, 6), context(state));
        assertThat(wrongQuestion.error()).isTrue();
        assertThat(wrongQuestion.content()).contains("question_no 2");

        ToolResult outOfRange = kit.tools.execute("score_answer", score(2, 11), context(state));
        assertThat(outOfRange.error()).isTrue();
        assertThat(outOfRange.content()).contains("score must be less than or equal to 10");
        assertThat(scores).isEmpty();
    }

    @Test
    void nearMissToolNamesAreAccepted() throws Exception {
        assertThat(kit.tools.execute("Score_Answer", score(1, 7), context(new InterviewState(PLAN, 1, 1, false))).error())
                .isFalse();
    }

    @Test
    void toolDefinitionsAreStrictSortedAndDeterministic() throws Exception {
        JsonNode tools = JSON.readTree(ObjectMappers.jsonMapper().writeValueAsString(kit.tools.apiTools()));
        assertThat(tools).extracting(tool -> tool.path("name").asText())
                .containsExactly("end_interview", "get_candidate_profile", "get_job_requirements", "score_answer");
        for (JsonNode tool : tools) {
            assertThat(tool.path("strict").asBoolean()).isTrue();
            assertThat(tool.path("input_schema").path("additionalProperties").asBoolean(true)).isFalse();
        }
        String again = ObjectMappers.jsonMapper().writeValueAsString(new AiTestKit("http://localhost:2").tools.apiTools());
        assertThat(again).isEqualTo(ObjectMappers.jsonMapper().writeValueAsString(kit.tools.apiTools()));
    }

    private ToolContext context(InterviewState state) {
        return new ToolContext(state, new InterviewHooks() {
            @Override
            public void status(String message) {}

            @Override
            public void token(String text) {}

            @Override
            public void draftReset() {}

            @Override
            public void scored(ScoreAnswerInput score) {
                scores.add(score);
            }

            @Override
            public void question(int questionNo, String question, AgentTranscript transcript) {}

            @Override
            public void ended(String summary) {}

            @Override
            public void checkpoint(AgentTranscript transcript) {}
        }, "{}", "{}");
    }

    private static JsonNode score(int questionNo, int score) throws Exception {
        return JSON.readTree("""
                {"question_no": %d, "score": %d, "strengths": ["Clear"], "weaknesses": ["Too short"],
                 "model_answer": "A strong answer would..."}""".formatted(questionNo, score));
    }
}
