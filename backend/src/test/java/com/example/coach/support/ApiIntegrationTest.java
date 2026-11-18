package com.example.coach.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.coach.service.MailService;
import com.example.coach.service.MailService.OutgoingEmail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base for tests through the real HTTP API with a real PostgreSQL and the real Anthropic SDK. The Claude API is
 * replaced by WireMock and outgoing email by a mock that records every message, so tests can follow the links in
 * it. Subclasses share one Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiIntegrationTest {

    protected static final WireMockServer CLAUDE = startWireMock();
    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final String HAIKU = "claude-haiku-5-5";
    protected static final String SONNET = "claude-sonnet-5-5";

    @Autowired
    protected TestRestTemplate rest;

    @MockitoBean
    protected MailService mail;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
        registry.add("coach.ai.base-url", CLAUDE::baseUrl);
        registry.add("coach.ai.api-key", () -> "test-key");
        registry.add("coach.interview.easy-questions", () -> "2"); // keep the scripted interview short
        registry.add("coach.security.email-resend-cooldown", () -> "0s");
        registry.add("coach.rate-limit.auth-requests-per-minute", () -> "1000");
    }

    /** Signs up, follows the link in the confirmation email and returns the resulting JWT. */
    protected String signUp(String firstName, String lastName, String email, String password) {
        call(null, HttpMethod.POST, "/api/auth/register", Map.of("firstName", firstName, "lastName", lastName,
                "email", email, "countryCode", "IN", "mobileNumber", "98765 43210", "password", password),
                HttpStatus.CREATED);
        JsonNode auth = call(null, HttpMethod.POST, "/api/auth/verify-email",
                Map.of("token", awaitLinkToken(email, "/verify-email", 1)), HttpStatus.OK);
        return auth.path("token").asText();
    }

    /** Emails sent so far to {@code to}, oldest first. Sending is asynchronous, see {@link #awaitLinkToken}. */
    protected List<OutgoingEmail> emailsTo(String to) {
        return Mockito.mockingDetails(mail).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> (OutgoingEmail) invocation.getArgument(0))
                .filter(email -> email.to().equals(to))
                .toList();
    }

    /** Waits until {@code count} emails with a {@code path} link reached {@code to}; returns the newest token. */
    protected String awaitLinkToken(String to, String path, int count) {
        Pattern link = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)");
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            List<OutgoingEmail> withLink = emailsTo(to).stream().filter(email -> link.matcher(email.text()).find())
                    .toList();
            if (withLink.size() >= count) {
                OutgoingEmail newest = withLink.getLast();
                assertThat(newest.html()).contains(path + "?token=");
                Matcher matcher = link.matcher(newest.text());
                assertThat(matcher.find()).isTrue();
                return matcher.group(1);
            }
            assertThat(System.currentTimeMillis()).as("waiting for an email to %s with a %s link", to, path)
                    .isLessThan(deadline);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    protected JsonNode call(String token, HttpMethod method, String path, Object body, HttpStatus expected) {
        HttpHeaders headers = token == null ? new HttpHeaders() : auth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(expected);
        return readTree(response.getBody());
    }

    protected static HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected JsonNode readTree(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(json, e);
        }
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }
}
