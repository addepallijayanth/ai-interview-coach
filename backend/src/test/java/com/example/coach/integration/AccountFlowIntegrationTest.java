package com.example.coach.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coach.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** Sign-up with email confirmation, sign-in, and password reset, through the real API and database. */
class AccountFlowIntegrationTest extends ApiIntegrationTest {

    private static final String EMAIL = "ravi.kumar@example.com";
    private static final String PASSWORD = "first-password-1";

    @Test
    void signUpConfirmTheEmailThenSignIn() {
        // Every field is validated, including the mobile number against the chosen country's numbering plan.
        JsonNode badPhone = call(null, HttpMethod.POST, "/api/auth/register", registration("12345"),
                HttpStatus.BAD_REQUEST);
        assertThat(badPhone.path("errors").path("mobileNumber").asText()).contains("India");

        JsonNode registered = call(null, HttpMethod.POST, "/api/auth/register", registration("+91 98765 43210"),
                HttpStatus.CREATED);
        assertThat(registered.path("email").asText()).isEqualTo(EMAIL);
        assertThat(registered.has("token")).isFalse(); // no session until the address is confirmed

        // The account exists but cannot be used yet, and signing up again does not create a second one.
        JsonNode blocked = call(null, HttpMethod.POST, "/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD),
                HttpStatus.FORBIDDEN);
        assertThat(blocked.path("code").asText()).isEqualTo("email_not_verified");
        JsonNode duplicate = call(null, HttpMethod.POST, "/api/auth/register", registration("98765 43210"),
                HttpStatus.CONFLICT);
        assertThat(duplicate.path("code").asText()).isEqualTo("email_not_verified");

        // Asking for a new link replaces the first one.
        String firstLink = awaitLinkToken(EMAIL, "/verify-email", 1);
        call(null, HttpMethod.POST, "/api/auth/resend-verification", Map.of("email", EMAIL), HttpStatus.ACCEPTED);
        String secondLink = awaitLinkToken(EMAIL, "/verify-email", 2);
        assertThat(secondLink).isNotEqualTo(firstLink);
        assertThat(call(null, HttpMethod.POST, "/api/auth/verify-email", Map.of("token", firstLink),
                HttpStatus.BAD_REQUEST).path("code").asText()).isEqualTo("invalid_link");

        // Following the link confirms the address and signs the user in; the link works once.
        JsonNode confirmed = call(null, HttpMethod.POST, "/api/auth/verify-email", Map.of("token", secondLink),
                HttpStatus.OK);
        assertThat(confirmed.path("token").asText()).isNotBlank();
        JsonNode user = confirmed.path("user");
        assertThat(user.path("emailVerified").asBoolean()).isTrue();
        assertThat(user.path("firstName").asText()).isEqualTo("Ravi");
        assertThat(user.path("lastName").asText()).isEqualTo("Kumar");
        assertThat(user.path("countryCode").asText()).isEqualTo("IN");
        assertThat(user.path("phoneNumber").asText()).isEqualTo("+919876543210");
        assertThat(call(null, HttpMethod.POST, "/api/auth/verify-email", Map.of("token", secondLink),
                HttpStatus.GONE).path("code").asText()).isEqualTo("link_used");

        String jwt = call(null, HttpMethod.POST, "/api/auth/login", Map.of("email", "Ravi.Kumar@Example.com",
                "password", PASSWORD), HttpStatus.OK).path("token").asText();
        assertThat(call(jwt, HttpMethod.GET, "/api/auth/me", null, HttpStatus.OK).path("email").asText())
                .isEqualTo(EMAIL);
        assertThat(call(null, HttpMethod.POST, "/api/auth/register", registration("98765 43210"),
                HttpStatus.CONFLICT).path("code").asText()).isEqualTo("conflict");
    }

    @Test
    void forgottenPasswordsAreResetWithAnEmailedLink() {
        String email = "meera.iyer@example.com";
        signUp("Meera", "Iyer", email, "old-password-1");

        // Unknown addresses get the same answer and no email.
        call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", "nobody@example.com"),
                HttpStatus.ACCEPTED);
        call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", email), HttpStatus.ACCEPTED);
        String reset = awaitLinkToken(email, "/reset-password", 1);
        assertThat(emailsTo("nobody@example.com")).isEmpty();

        JsonNode signedIn = call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", reset, "password", "new-password-2"), HttpStatus.OK);
        assertThat(signedIn.path("token").asText()).isNotBlank();

        call(null, HttpMethod.POST, "/api/auth/login", Map.of("email", email, "password", "old-password-1"),
                HttpStatus.UNAUTHORIZED);
        call(null, HttpMethod.POST, "/api/auth/login", Map.of("email", email, "password", "new-password-2"),
                HttpStatus.OK);
        assertThat(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", reset, "password", "third-password-3"), HttpStatus.GONE).path("code").asText())
                .isEqualTo("link_used");
    }

    private static Map<String, String> registration(String mobileNumber) {
        Map<String, String> body = new HashMap<>();
        body.put("firstName", "Ravi");
        body.put("lastName", "Kumar");
        body.put("email", EMAIL);
        body.put("countryCode", "IN");
        body.put("mobileNumber", mobileNumber);
        body.put("password", PASSWORD);
        return body;
    }
}
