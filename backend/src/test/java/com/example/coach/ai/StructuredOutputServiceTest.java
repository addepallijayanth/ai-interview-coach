package com.example.coach.ai;

import static com.example.coach.support.AnthropicStubs.message;
import static com.example.coach.support.AnthropicStubs.refusal;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coach.ai.model.MatchResult;
import com.example.coach.exception.AiException;
import com.example.coach.support.AiTestKit;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructuredOutputServiceTest {

    private static final String SONNET = "claude-sonnet-5-5";
    private static final String RESUME = "- Built Spring Boot microservices processing 2 million payments per day";

    private WireMockServer claude;
    private AiTestKit kit;

    @BeforeEach
    void start() {
        claude = new WireMockServer(options().dynamicPort());
        claude.start();
        kit = new AiTestKit(claude.baseUrl());
    }

    @AfterEach
    void stop() {
        claude.stop();
    }

    @Test
    void invalidOutputIsRetriedOnceWithTheProblemsFedBack() {
        respondInOrder(message(SONNET, match(140, "Built Spring Boot microservices processing 2 million payments per day")),
                message(SONNET, match(80, "Built Spring Boot microservices processing 2 million payments per day")));

        Structured<MatchResult> result = kit.structured.generate(request());

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.value().matchScore()).isEqualTo(80);
        List<LoggedRequest> requests = claude.findAll(postRequestedFor(urlEqualTo("/v1/messages")));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getBodyAsString()).doesNotContain("validation_feedback");
        assertThat(requests.get(1).getBodyAsString())
                .contains("validation_feedback", "matchScore must be less than or equal to 100");
    }

    @Test
    void outputThatIsStillInvalidAfterTheRetryIsRepairedWhenPossible() {
        String invented = match(75, "Led a team of 50 engineers at a FAANG company");
        respondInOrder(message(SONNET, invented), message(SONNET, invented));

        Structured<MatchResult> result = kit.structured.generate(request());

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.value().suggestions()).isEmpty(); // the hallucinated bullet was dropped
        assertThat(result.value().matchScore()).isEqualTo(75);
    }

    @Test
    void truncatedOutputIsRetried() {
        respondInOrder(message(SONNET, "max_tokens", "{\"matchScore\": 8"),
                message(SONNET, match(64, "Built Spring Boot microservices processing 2 million payments per day")));
        assertThat(kit.structured.generate(request()).value().matchScore()).isEqualTo(64);
    }

    @Test
    void refusalsSurfaceAsAiExceptionsWithoutRetrying() {
        claude.stubFor(post("/v1/messages").willReturn(refusal(SONNET)));
        assertThatThrownBy(() -> kit.structured.generate(request()))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getKind()).isEqualTo(AiException.Kind.REFUSED));
        assertThat(claude.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).hasSize(1);
    }

    private StructuredRequest<MatchResult> request() {
        return StructuredRequest.of(AiTask.MATCH, 1L, "You compare resumes.", "Compare these.", "match-result",
                MatchResult.class).withChecks(m -> MatchChecks.problems(m, RESUME), m -> MatchChecks.repair(m, RESUME));
    }

    private void respondInOrder(ResponseDefinitionBuilder first, ResponseDefinitionBuilder second) {
        claude.stubFor(post("/v1/messages").inScenario("attempts").whenScenarioStateIs(STARTED)
                .willSetStateTo("retry").willReturn(first));
        claude.stubFor(post("/v1/messages").inScenario("attempts").whenScenarioStateIs("retry").willReturn(second));
    }

    private static String match(int score, String original) {
        return """
                {"matchScore": %d, "matchedSkills": ["Java"], "missingSkills": ["Kafka"], "strengths": ["Payments"],
                 "suggestions": [{"original": "%s", "improved": "Better bullet", "reason": "Why"}],
                 "summary": "You are a good match."}""".formatted(score, original);
    }
}
