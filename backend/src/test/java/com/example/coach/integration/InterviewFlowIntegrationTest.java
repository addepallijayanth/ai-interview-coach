package com.example.coach.integration;

import static com.example.coach.support.AnthropicStubs.message;
import static com.example.coach.support.AnthropicStubs.stream;
import static com.example.coach.support.AnthropicStubs.text;
import static com.example.coach.support.AnthropicStubs.thinking;
import static com.example.coach.support.AnthropicStubs.toolUse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.coach.support.ApiIntegrationTest;
import com.example.coach.support.SseEvents;
import com.example.coach.support.SseEvents.Event;
import com.example.coach.support.TestDocuments;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * End to end through the real HTTP API, a real PostgreSQL and the real Anthropic SDK, with the Claude API
 * replaced by WireMock: sign up and confirm the email, upload, analyse, a two-question Easy interview over SSE
 * (an introduction, then a technical question), then the report and PDF.
 */
class InterviewFlowIntegrationTest extends ApiIntegrationTest {

    @Test
    void fullInterviewFlow() throws Exception {
        stubAnalysis();
        stubInterview();
        String token = signUp("Asha", "Verma", "asha@example.com", "correct-horse-battery-9");

        // Upload: text is extracted from a real PDF and contact details are masked before storage.
        JsonNode resume = upload(token, TestDocuments.pdf(TestDocuments.RESUME));
        assertThat(resume.path("redactedText").asText())
                .contains("[EMAIL]", "[PHONE]", "[PROFILE_URL]", "[ADDRESS]")
                .doesNotContain("asha.verma@example.com", "98765");

        JsonNode jd = call(token, HttpMethod.POST, "/api/job-descriptions", Map.of("text", TestDocuments.JOB),
                HttpStatus.CREATED);

        // Analysis: two parallel Haiku extractions, then one Sonnet comparison.
        JsonNode analysis = call(token, HttpMethod.POST, "/api/analyses",
                Map.of("resumeId", resume.path("id").asLong(), "jdId", jd.path("id").asLong()), HttpStatus.CREATED);
        assertThat(analysis.path("matchScore").asInt()).isEqualTo(74);
        assertThat(analysis.path("match").path("missingSkills").toString()).contains("Kafka");
        assertThat(analysis.path("modelUsed").asText()).contains(HAIKU, SONNET);
        assertThat(analysis.path("costUsd").decimalValue()).isPositive();

        // Interview: an Easy interview (two questions in this test: HR, then Technical). The stream runs the agent
        // until question 1 is asked.
        JsonNode session = call(token, HttpMethod.POST, "/api/interviews",
                Map.of("analysisId", analysis.path("id").asLong(), "level", "EASY"), HttpStatus.CREATED);
        long sessionId = session.path("id").asLong();
        assertThat(session.path("level").asText()).isEqualTo("EASY");
        assertThat(session.path("questionTarget").asInt()).isEqualTo(2);

        List<Event> first = openStream(token, sessionId);
        assertThat(SseEvents.names(first)).contains("status", "token", "draft_reset", "question", "done");
        JsonNode question1 = SseEvents.first(first, "question").orElseThrow().data();
        assertThat(question1.path("question").asText()).isEqualTo("Tell me about yourself and why this role.");
        assertThat(question1.path("category").asText()).isEqualTo("HR");

        // Reloading the page replays the open question without calling the model again.
        int callsBefore = CLAUDE.findAll(postRequestedFor(urlEqualTo("/v1/messages"))).size();
        assertThat(SseEvents.names(openStream(token, sessionId))).containsExactly("question", "done");
        assertThat(CLAUDE.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).hasSize(callsBefore);

        call(token, HttpMethod.POST, "/api/interviews/" + sessionId + "/answers",
                Map.of("answer", "I build payment services in Java and want to own a platform like yours."),
                HttpStatus.ACCEPTED);
        List<Event> second = openStream(token, sessionId);
        JsonNode feedback = SseEvents.first(second, "feedback").orElseThrow().data();
        assertThat(feedback.path("score").asInt()).isEqualTo(7);
        JsonNode question2 = SseEvents.first(second, "question").orElseThrow().data();
        assertThat(question2.path("questionNo").asInt()).isEqualTo(2);
        assertThat(question2.path("category").asText()).isEqualTo("TECHNICAL");
        assertThat(question2.path("categoryLabel").asText()).isEqualTo("Technical");

        call(token, HttpMethod.POST, "/api/interviews/" + sessionId + "/answers",
                Map.of("answer", "I would use an idempotency key stored with a unique constraint."),
                HttpStatus.ACCEPTED);
        List<Event> third = openStream(token, sessionId);
        assertThat(SseEvents.names(third)).contains("feedback", "ended");
        JsonNode ended = SseEvents.first(third, "ended").orElseThrow().data();
        assertThat(ended.path("overallScore").decimalValue()).isEqualByComparingTo("8.0");

        JsonNode finished = call(token, HttpMethod.GET, "/api/interviews/" + sessionId, null, HttpStatus.OK);
        assertThat(finished.path("status").asText()).isEqualTo("ENDED");
        assertThat(finished.path("questionsAnswered").asInt()).isEqualTo(2);
        assertThat(finished.path("turns").get(0).path("category").asText()).isEqualTo("HR");

        // Report: scores per category, a study plan generated once (Haiku), and the PDF renders.
        JsonNode report = call(token, HttpMethod.GET, "/api/reports/" + sessionId, null, HttpStatus.OK);
        assertThat(report.path("levelLabel").asText()).isEqualTo("Easy");
        assertThat(report.path("categoryScores").toString())
                .contains("\"category\":\"TECHNICAL\"", "\"averageScore\":9.0", "\"category\":\"HR\"");
        assertThat(report.path("studyPlan").path("focusAreas").get(0).path("topic").asText()).isEqualTo("Kafka");
        assertThat(report.path("aiCostUsd").decimalValue()).isPositive();
        ResponseEntity<byte[]> pdf = rest.exchange("/api/reports/" + sessionId + ".pdf", HttpMethod.GET,
                new HttpEntity<>(auth(token)), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5)).isEqualTo("%PDF-");

        assertRequestContracts();
    }

    /** What the backend actually sent to Claude: routing, caching, structured output, strict tools, replay. */
    private void assertRequestContracts() throws Exception {
        List<JsonNode> requests = CLAUDE.findAll(postRequestedFor(urlEqualTo("/v1/messages"))).stream()
                .map(LoggedRequest::getBodyAsString).map(this::readTree).toList();

        JsonNode extraction = requests.stream().filter(r -> r.path("model").asText().equals(HAIKU)
                && r.toString().contains("You extract structured facts")).findFirst().orElseThrow();
        assertThat(extraction.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(extraction.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(extraction.has("fallbacks")).isFalse(); // Haiku has no server-side fallback

        JsonNode match = requests.stream().filter(r -> r.toString().contains("experienced technical recruiter"))
                .findFirst().orElseThrow();
        assertThat(match.path("model").asText()).isEqualTo(SONNET);
        assertThat(match.path("fallbacks").asText()).isEqualTo("default");

        List<JsonNode> interview = requests.stream().filter(r -> r.has("tools")).toList();
        assertThat(interview).hasSize(6);
        JsonNode firstTurn = interview.getFirst();
        assertThat(firstTurn.path("stream").asBoolean()).isTrue();
        assertThat(firstTurn.path("system").get(1).path("text").asText())
                .contains("Level: Easy.", "Planned number of questions: 2", "1: HR\n2: Technical");
        assertThat(firstTurn.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(firstTurn.path("system").get(1).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(firstTurn.path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        for (JsonNode tool : firstTurn.path("tools")) {
            assertThat(tool.path("strict").asBoolean()).isTrue();
            assertThat(tool.path("eager_input_streaming").asBoolean()).isTrue();
        }
        // The tool list and system prompt are byte-identical on every turn (cache and replay friendly).
        for (JsonNode turn : interview) {
            assertThat(turn.path("tools")).isEqualTo(firstTurn.path("tools"));
            assertThat(turn.path("system")).isEqualTo(firstTurn.path("system"));
        }
        // The assistant turn, including its thinking-block signature, is replayed unchanged on the next request.
        JsonNode replayed = interview.get(1).path("messages").get(1).path("content");
        assertThat(replayed.get(0).path("type").asText()).isEqualTo("thinking");
        assertThat(replayed.get(0).path("signature").asText()).isEqualTo("sig-kickoff");
        assertThat(replayed.get(2).path("name").asText()).isEqualTo("get_candidate_profile");
        // Each later request extends the previous one (append-only history).
        for (int i = 1; i < interview.size(); i++) {
            JsonNode previous = interview.get(i - 1).path("messages");
            JsonNode current = interview.get(i).path("messages");
            for (int m = 0; m < previous.size(); m++) {
                assertThat(current.get(m)).as("message %d of request %d", m, i).isEqualTo(previous.get(m));
            }
        }
    }

    private void stubAnalysis() {
        CLAUDE.stubFor(post("/v1/messages").withRequestBody(containing("You extract structured facts"))
                .willReturn(message(HAIKU, """
                        {"skills":["Java","Spring Boot","PostgreSQL","Docker","Kubernetes","AWS"],"yearsExperience":5,
                         "roles":["Senior Java Developer"],"seniority":"senior","education":"B.Tech Computer Science",
                         "highlights":["Built Spring Boot microservices processing 2 million payments per day"]}""")));
        CLAUDE.stubFor(post("/v1/messages").withRequestBody(containing("You extract structured hiring requirements"))
                .willReturn(message(HAIKU, """
                        {"title":"Backend Engineer (Java)","company":"Globex","seniority":"mid","yearsRequired":4,
                         "requiredSkills":["Java","Spring Boot","PostgreSQL","Kafka","Kubernetes"],
                         "niceToHaveSkills":["Terraform","GraphQL"],
                         "responsibilities":["Design APIs","Own services in production","Mentor engineers"]}""")));
        CLAUDE.stubFor(post("/v1/messages").withRequestBody(containing("experienced technical recruiter"))
                .willReturn(message(SONNET, """
                        {"matchScore":74,"matchedSkills":["Java","Spring Boot","PostgreSQL","Kubernetes"],
                         "missingSkills":["Kafka","Terraform"],"strengths":["Payments domain experience"],
                         "suggestions":[{"original":"Built Spring Boot microservices processing 2 million payments per day",
                           "improved":"Designed and ran Spring Boot payment microservices handling 2M transactions/day with [X]% uptime",
                           "reason":"Shows ownership in production, which the role asks for."}],
                         "summary":"You are a strong match; event streaming is the main gap."}""")));
        CLAUDE.stubFor(post("/v1/messages").withRequestBody(containing("You are a career coach"))
                .willReturn(message(HAIKU, """
                        {"summary":"Focus on event streaming next.","focusAreas":[{"topic":"Kafka","priority":"high",
                         "why":"Required by the job and missing from your resume.",
                         "actions":["Build a small producer/consumer service"],"resources":["Kafka: The Definitive Guide"]}]}""")));
    }

    private void stubInterview() {
        String[][] steps = {
            {STARTED, "q1"}, {"q1", "a1"}, {"a1", "q2"}, {"q2", "a2"}, {"a2", "end"}, {"end", "done"}};
        List<com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder> responses = List.of(
                stream(SONNET, "tool_use", List.of(thinking("sig-kickoff"), text("Let me look at your profile."),
                        toolUse("get_candidate_profile", "{}"), toolUse("get_job_requirements", "{}"))),
                stream(SONNET, "end_turn", List.of(thinking("sig-q1"), text("Tell me about yourself and why this role."))),
                stream(SONNET, "tool_use", List.of(toolUse("score_answer", """
                        {"question_no":1,"score":7,"strengths":["Clear motivation"],"weaknesses":["No concrete impact"],
                         "model_answer":"I'm a backend engineer who has spent five years on payments..."}"""))),
                stream(SONNET, "end_turn", List.of(text("How would you design an idempotent payment API?"))),
                stream(SONNET, "tool_use", List.of(toolUse("score_answer", """
                        {"question_no":2,"score":9,"strengths":["Idempotency keys"],"weaknesses":[],
                         "model_answer":"Accept an Idempotency-Key header, store it with the result..."}"""))),
                stream(SONNET, "tool_use", List.of(toolUse("end_interview",
                        "{\"summary\":\"Solid fundamentals; practise retry semantics.\"}"))));
        for (int i = 0; i < steps.length; i++) {
            CLAUDE.stubFor(post("/v1/messages").withRequestBody(matchingJsonPath("$.tools"))
                    .inScenario("interview").whenScenarioStateIs(steps[i][0]).willSetStateTo(steps[i][1])
                    .willReturn(responses.get(i)));
        }
    }

    private JsonNode upload(String token, byte[] pdf) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "asha-verma.pdf";
            }
        });
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.postForEntity("/api/resumes", new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return readTree(response.getBody());
    }

    private List<Event> openStream(String token, long sessionId) {
        HttpHeaders headers = auth(token);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        ResponseEntity<String> response = rest.exchange("/api/interviews/" + sessionId + "/stream", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        List<Event> events = SseEvents.parse(response.getBody());
        assertThat(SseEvents.names(events)).as(response.getBody()).doesNotContain("error");
        return events;
    }
}
