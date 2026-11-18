package com.example.coach.controller;

import com.example.coach.dto.AuthDtos.AuthResponse;
import com.example.coach.dto.AuthDtos.EmailRequest;
import com.example.coach.dto.AuthDtos.LoginRequest;
import com.example.coach.dto.AuthDtos.MessageResponse;
import com.example.coach.dto.AuthDtos.RegisterRequest;
import com.example.coach.dto.AuthDtos.RegistrationResponse;
import com.example.coach.dto.AuthDtos.ResetPasswordRequest;
import com.example.coach.dto.AuthDtos.UserResponse;
import com.example.coach.dto.AuthDtos.VerifyEmailRequest;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account; a confirmation link is emailed to the address")
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in and receive a JWT (the email address must be confirmed)")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address with the token from the emailed link, and sign in")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Email a new confirmation link (same response whether or not the account exists)")
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
        return new MessageResponse("If that email belongs to an account that still needs confirming, a new link "
                + "is on its way.");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Email a password-reset link (same response whether or not the account exists)")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.forgotPassword(request.email());
        return new MessageResponse("If that email belongs to an account, a password reset link is on its way.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password with the token from the emailed link, and sign in")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    public UserResponse me(@CurrentUserId Long userId) {
        return authService.me(userId);
    }
}
