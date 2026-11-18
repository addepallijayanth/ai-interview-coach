package com.example.coach.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    /** Letters (any alphabet), spaces, apostrophes, hyphens and dots, starting with a letter. */
    static final String NAME_PATTERN = "^[\\p{L}\\p{M}][\\p{L}\\p{M}' .-]*$";
    static final String NAME_MESSAGE = "can only contain letters, spaces, apostrophes, hyphens and dots";
    /** At least one letter and one digit. */
    static final String PASSWORD_PATTERN = "(?s)^(?=.*\\p{L})(?=.*\\p{N}).*$";
    static final String PASSWORD_MESSAGE = "must include at least one letter and one number";
    /** Bean Validation's @Email accepts "a@b"; real sign-ups need a domain with a dot. */
    static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    static final String EMAIL_MESSAGE = "must be a valid email address";

    public record RegisterRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String firstName,
            @NotBlank @Size(max = 60) @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String lastName,
            @NotBlank @Email(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE) @Size(max = 255) String email,
            /** ISO 3166-1 alpha-2 code of the mobile number's country, e.g. "IN" (dialling code +91). */
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a two-letter country code") String countryCode,
            /** National format ("98765 43210") or with the dialling code ("+91 98765 43210"). */
            @NotBlank @Size(max = 25) @Pattern(regexp = "^[0-9 ()+.-]+$",
                    message = "can only contain digits, spaces and + ( ) - .") String mobileNumber,
            // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected rather than truncated.
            @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password) {}

    /** Sign-up succeeded; the account works once the emailed link is clicked. */
    public record RegistrationResponse(String email, String message) {}

    public record MessageResponse(String message) {}

    public record UserResponse(
            Long id,
            String email,
            String firstName,
            String lastName,
            String countryCode,
            /** E.164, e.g. +919876543210. */
            String phoneNumber,
            boolean emailVerified,
            Instant createdAt) {}

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {}

    /** A country for the sign-up form: ISO code, English name and dialling code, e.g. IN, India, +91. */
    public record CountryResponse(String code, String name, String dialCode) {}
}
