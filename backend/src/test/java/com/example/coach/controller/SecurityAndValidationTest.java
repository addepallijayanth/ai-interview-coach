package com.example.coach.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coach.config.CoachProperties;
import com.example.coach.config.SecurityConfig;
import com.example.coach.dto.AuthDtos.AuthResponse;
import com.example.coach.dto.AuthDtos.UserResponse;
import com.example.coach.exception.ApiException;
import com.example.coach.security.ProblemJsonAuthenticationEntryPoint;
import com.example.coach.security.RateLimiter;
import com.example.coach.service.AuthService;
import com.example.coach.service.ResumeService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP-layer behaviour: authentication, problem+json errors, validation and rate limiting. */
@WebMvcTest(controllers = {AuthController.class, ResumeController.class})
@Import({SecurityConfig.class, ProblemJsonAuthenticationEntryPoint.class, RateLimiter.class})
class SecurityAndValidationTest {

    /** The MVC slice does not scan configuration properties; security and rate limiting need them. */
    @TestConfiguration
    @EnableConfigurationProperties(CoachProperties.class)
    static class Properties {}

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    ResumeService resumeService;

    @Test
    void protectedEndpointsReturnProblemJsonWithoutAToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void forgedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_expired"));
    }

    @Test
    void theUserIdComesFromTheTokenSubject() throws Exception {
        when(authService.me(42L)).thenReturn(user(42L, "dev@example.com"));
        mvc.perform(get("/api/auth/me").with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@example.com"))
                .andExpect(jsonPath("$.firstName").value("Dev"));
    }

    @Test
    void invalidRegistrationsListEveryBadField() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "R2-D2", "email": "not-an-email", "countryCode": "India",
                                 "mobileNumber": "call me", "password": "short1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.firstName").value("can only contain letters, spaces, apostrophes, hyphens and dots"))
                .andExpect(jsonPath("$.errors.lastName").value("must not be blank"))
                .andExpect(jsonPath("$.errors.email").value("must be a valid email address"))
                .andExpect(jsonPath("$.errors.countryCode").value("must be a two-letter country code"))
                .andExpect(jsonPath("$.errors.mobileNumber").exists())
                .andExpect(jsonPath("$.errors.password").value("must be between 8 and 72 characters"));
    }

    @Test
    void passwordsNeedALetterAndANumber() throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"onlyletters\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("must include at least one letter and one number"));
    }

    @Test
    void accountRecoveryEndpointsArePublicAndDoNotRevealWhetherAnAccountExists() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If that email belongs to an account, a password reset link is on its way."));
        verify(authService).forgotPassword("nobody@example.com");

        mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").exists());
    }

    @Test
    void signInIsRefusedUntilTheEmailIsConfirmed() throws Exception {
        when(authService.login(any())).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "email_not_verified",
                "Please confirm your email address first."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"password1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email_not_verified"));
    }

    @Test
    void signInAttemptsAreRateLimitedPerClient() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("token", Instant.now(), user(1L, "a@b.co")));
        String body = "{\"email\":\"a@b.co\",\"password\":\"password1\"}";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                    .with(request -> { request.setRemoteAddr("203.0.113.7"); return request; }))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(request -> { request.setRemoteAddr("203.0.113.7"); return request; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void unsupportedUploadsAre415() throws Exception {
        when(resumeService.upload(eq(7L), any())).thenThrow(ApiException.unsupportedFile("Upload your resume as a PDF or DOCX file."));
        mvc.perform(multipart("/api/resumes")
                        .file(new MockMultipartFile("file", "cv.png", "image/png", new byte[] {1, 2, 3}))
                        .with(jwt().jwt(token -> token.subject("7"))))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_file"));
    }

    private static UserResponse user(long id, String email) {
        return new UserResponse(id, email, "Dev", "Eloper", "IN", "+919876543210", true, Instant.now());
    }
}
