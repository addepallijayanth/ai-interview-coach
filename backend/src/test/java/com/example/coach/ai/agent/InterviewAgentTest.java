package com.example.coach.ai.agent;

import static com.example.coach.support.AnthropicStubs.stream;
import static com.example.coach.support.AnthropicStubs.text;
import static com.example.coach.support.AnthropicStubs.toolUse;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.models.messages.MessageParam;
import com.example.coach.ai.tools.ScoreAnswerInput;
import com.example.coach.ai.tools.ToolContext;
import com.example.coach.exception.AiException;
import com.example.coach.support.AiTestKit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The agent loop's guard rails, against a scripted Claude. */
class InterviewAgentTest {

    private static final String SONNET = "claude-sonnet-5-5";
    private static final ObjectMapper JSON = new ObjectMapper();

    private WireMockServer claude;
    private AiTestKit kit;
    private RecordingHooks hooks;

    @BeforeEach
    void start() {
        claude = new WireMockServer(options().dynamicPort());
        claude.start();
        kit = new AiTestKit(claude.baseUrl());
        hooks = new RecordingHooks();
    }

    @AfterEach
    void stop() {
        claude.stop();
    }

    @Test
    void stopsAtTheStepLimitInsteadOfLoopingForever() {
        claude.stubFor(post("/v1/messages").willReturn(stream(SONNET, "tool_use",
                List.of(toolUse("get_candidate_profile", "{}")))));

        InterviewAgent.Outcome outcome = kit.agent.start(run(AgentTranscript.empty(), new InterviewState(plan(3), 0, null, false)));

        assertThat(outcome).isEqualTo(InterviewAgent.Outcome.STEP_LIMIT);
        assertThat(requests()).hasSize(kit.properties.interview().maxAgentSteps());
    }

    @Test
    void aQuestionAskedBeforeScoringIsDroppedAndTheAgentIsSteered() throws Exception {
        respondInOrder(
                stream(SONNET, "end_turn", List.of(text("Great! Next: what is garbage collection?"))),
                stream(SONNET, "tool_use", List.of(toolUse("score_answer", """
                        {"question_no":1,"score":6,"strengths":["Correct basics"],"weaknesses":["No complexity"],
                         "model_answer":"A hash map stores entries in buckets..."}"""))),
                stream(SONNET, "end_turn", List.of(text("How does garbage collection work in the JVM?"))));
        AgentTranscript transcript = AgentTranscript.empty();
        transcript.add(MessageParam.ofUser("Hi! I'm ready to start the mock interview."));
        transcript.add(MessageParam.ofAssistant("How does a HashMap work?"));
        InterviewState state = new InterviewState(plan(3), 1, null, false);

        InterviewAgent.Outcome outcome = kit.agent.answer(run(transcript, state), 1, "It stores key-value pairs.");

        assertThat(outcome).isEqualTo(InterviewAgent.Outcome.AWAITING_ANSWER);
        assertThat(hooks.scores).extracting(ScoreAnswerInput::score).containsExactly(6);
        assertThat(hooks.questions).containsExactly("How does garbage collection work in the JVM?");
        assertThat(hooks.events).contains("draft_reset");
        assertThat(state.questionsAsked()).isEqualTo(2);

        JsonNode steered = body(requests().get(1)).path("messages");
        JsonNode correction = steered.get(steered.size() - 1);
        assertThat(correction.path("role").asText()).isEqualTo("system");
        assertThat(correction.toString()).contains("call score_answer for question 1");
        for (LoggedRequest request : requests().subList(1, 3)) {
            assertThat(request.getBodyAsString()).doesNotContain("what is garbage collection?");
        }
    }

    @Test
    void endInterviewIsRejectedUntilTheInterviewIsFinished() throws Exception {
        respondInOrder(
                stream(SONNET, "tool_use", List.of(toolUse("end_interview", "{\"summary\":\"Done early.\"}"))),
                stream(SONNET, "end_turn", List.of(text("What is a deadlock?"))));

        kit.agent.start(run(AgentTranscript.empty(), new InterviewState(plan(3), 0, null, false)));

        assertThat(hooks.summary).isNull();
        JsonNode toolResult = lastMessage(requests().get(1)).path("content").get(0);
        assertThat(toolResult.path("is_error").asBoolean()).isTrue();
        assertThat(toolResult.path("content").asText()).contains("not finished");
        assertThat(hooks.questions).containsExactly("What is a deadlock?");
    }

    @Test
    void endingEarlySendsAnOperatorInstructionAndClosesWithTheModelsSummary() throws Exception {
        claude.stubFor(post("/v1/messages").willReturn(stream(SONNET, "tool_use",
                List.of(toolUse("end_interview", "{\"summary\":\"Good start; practise system design.\"}")))));
        AgentTranscript transcript = AgentTranscript.empty();
        transcript.add(MessageParam.ofUser("Hi! I'm ready to start the mock interview."));
        transcript.add(MessageParam.ofAssistant("Design a URL shortener."));

        InterviewAgent.Outcome outcome = kit.agent.endEarly(run(transcript, new InterviewState(plan(5), 2, null, false)));

        assertThat(outcome).isEqualTo(InterviewAgent.Outcome.ENDED);
        assertThat(hooks.summary).isEqualTo("Good start; practise system design.");
        JsonNode messages = body(requests().getFirst()).path("messages");
        assertThat(messages.get(messages.size() - 2).path("role").asText()).isEqualTo("user");
        JsonNode instruction = messages.get(messages.size() - 1);
        assertThat(instruction.path("role").asText()).isEqualTo("system"); // operator channel, after a user turn
        assertThat(instruction.toString()).contains("ended the interview early", "Call end_interview");
    }

    @Test
    void unknownToolsComeBackAsToolErrors() throws Exception {
        respondInOrder(
                stream(SONNET, "tool_use", List.of(toolUse("lookup_salary", "{}"))),
                stream(SONNET, "end_turn", List.of(text("Tell me about your last project."))));

        kit.agent.start(run(AgentTranscript.empty(), new InterviewState(plan(3), 0, null, false)));

        JsonNode toolResult = lastMessage(requests().get(1)).path("content").get(0);
        assertThat(toolResult.path("type").asText()).isEqualTo("tool_result");
        assertThat(toolResult.path("is_error").asBoolean()).isTrue();
        assertThat(toolResult.path("content").asText()).contains("Unknown tool 'lookup_salary'");
    }

    @Test
    void aRefusalStopsTheRunWithoutPersistingAnything() {
        claude.stubFor(post("/v1/messages").willReturn(stream(SONNET, "refusal", List.of())));

        assertThatThrownBy(() -> kit.agent.start(run(AgentTranscript.empty(), new InterviewState(plan(3), 0, null, false))))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getKind()).isEqualTo(AiException.Kind.REFUSED));
        assertThat(hooks.checkpoints).isZero();
        assertThat(hooks.questions).isEmpty();
    }

    @Test
    void transientApiErrorsAreReportedAsRetryable() {
        claude.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(529)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}")));

        assertThatThrownBy(() -> kit.agent.start(run(AgentTranscript.empty(), new InterviewState(plan(3), 0, null, false))))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getKind()).isEqualTo(AiException.Kind.BUSY));
    }

    @Test
    void theSystemPromptCarriesTheLevelAndTheQuestionPlan() throws Exception {
        claude.stubFor(post("/v1/messages").willReturn(stream(SONNET, "end_turn",
                List.of(text("Tell me about yourself and what draws you to this role.")))));
        InterviewState state = new InterviewState(InterviewPlan.build(InterviewLevel.EASY, 10), 0, null, false);

        kit.agent.start(new InterviewAgent.Run(1L, 99L, kit.agent.systemPrompt(InterviewLevel.EASY, state.plan(),
                "Java Engineer", "Resume text", "Job text"), AgentTranscript.empty(),
                new ToolContext(state, hooks, "{}", "{}")));

        JsonNode system = body(requests().getFirst()).path("system");
        String rules = system.get(0).path("text").asText();
        String context = system.get(1).path("text").asText();
        assertThat(rules).contains("- System design: Design something end to end", "- HR: Motivation and fit")
                .doesNotContain("{{");
        assertThat(context).contains("Level: Easy.", "Planned number of questions: 10", "1: HR", "2: Project deep-dive",
                "10: HR");
    }

    private InterviewAgent.Run run(AgentTranscript transcript, InterviewState state) {
        return new InterviewAgent.Run(1L, 99L, kit.agent.systemPrompt(InterviewLevel.STANDARD, state.plan(),
                "Java Engineer", "Resume text", "Job text"), transcript,
                new ToolContext(state, hooks, "{\"skills\":[\"Java\"]}", "{\"requiredSkills\":[\"Java\"]}"));
    }

    private static List<QuestionCategory> plan(int questions) {
        return Collections.nCopies(questions, QuestionCategory.TECHNICAL);
    }

    private void respondInOrder(ResponseDefinitionBuilder... responses) {
        for (int i = 0; i < responses.length; i++) {
            claude.stubFor(post("/v1/messages").inScenario("turns")
                    .whenScenarioStateIs(i == 0 ? STARTED : "step-" + i)
                    .willSetStateTo("step-" + (i + 1))
                    .willReturn(responses[i]));
        }
    }

    private List<LoggedRequest> requests() {
        return claude.findAll(postRequestedFor(urlEqualTo("/v1/messages")));
    }

    private static JsonNode body(LoggedRequest request) throws Exception {
        return JSON.readTree(request.getBodyAsString());
    }

    private static JsonNode lastMessage(LoggedRequest request) throws Exception {
        JsonNode messages = body(request).path("messages");
        return messages.get(messages.size() - 1);
    }

    static final class RecordingHooks implements InterviewHooks {
        final List<String> events = new ArrayList<>();
        final List<ScoreAnswerInput> scores = new ArrayList<>();
        final List<String> questions = new ArrayList<>();
        String summary;
        int checkpoints;

        @Override
        public void status(String message) {
            events.add("status");
        }

        @Override
        public void token(String text) {
            events.add("token");
        }

        @Override
        public void draftReset() {
            events.add("draft_reset");
        }

        @Override
        public void scored(ScoreAnswerInput score) {
            scores.add(score);
        }

        @Override
        public void question(int questionNo, String question, AgentTranscript transcript) {
            questions.add(question);
        }

        @Override
        public void ended(String summary) {
            this.summary = summary;
        }

        @Override
        public void checkpoint(AgentTranscript transcript) {
            checkpoints++;
        }
    }
}
