package com.example.coach.service;

import com.example.coach.config.CoachProperties;
import com.example.coach.dto.AuthDtos.AuthResponse;
import com.example.coach.dto.AuthDtos.LoginRequest;
import com.example.coach.dto.AuthDtos.RegisterRequest;
import com.example.coach.dto.AuthDtos.RegistrationResponse;
import com.example.coach.dto.AuthDtos.ResetPasswordRequest;
import com.example.coach.dto.AuthDtos.UserResponse;
import com.example.coach.exception.ApiException;
import com.example.coach.model.EmailToken;
import com.example.coach.model.EmailToken.Purpose;
import com.example.coach.model.User;
import com.example.coach.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts: sign-up with email confirmation, sign-in, and password reset by email. Accounts cannot sign in until
 * the address is confirmed; following a password-reset link also confirms it, so the owner of an address can
 * always recover an account that someone else signed up with.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailTokenService tokens;
    private final AccountMailer mailer;
    private final PhoneNumberService phones;
    private final CoachProperties.Security settings;
    /** Compared against when the email is unknown, so login takes the same time either way. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            EmailTokenService tokens, AccountMailer mailer, PhoneNumberService phones, CoachProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokens = tokens;
        this.mailer = mailer;
        this.phones = phones;
        this.settings = properties.security();
        this.dummyHash = passwordEncoder.encode("timing-attack-padding");
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        Optional<User> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            throw existing.get().isEmailVerified()
                    ? ApiException.conflict("An account with this email already exists. Sign in, or reset your "
                            + "password if you have forgotten it.")
                    : new ApiException(HttpStatus.CONFLICT, "email_not_verified", "You have already signed up with "
                            + "this email but haven't confirmed it yet. Check your inbox, or send a new link.");
        }
        String country = request.countryCode().strip().toUpperCase(Locale.ROOT);
        String phone = phones.normalizeMobile(country, request.mobileNumber());
        User user = users.save(new User(email, passwordEncoder.encode(request.password()),
                tidy(request.firstName()), tidy(request.lastName()), country, phone));
        mailer.sendVerification(user, tokens.issue(user, Purpose.VERIFY_EMAIL, settings.emailVerificationTtl()));
        return new RegistrationResponse(email, "We sent a confirmation link to " + email
                + ". Click it to activate your account.");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmailIgnoreCase(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Incorrect email or password.");
        }
        if (!user.get().isEmailVerified()) {
            // Only revealed after the password check, so it tells an attacker nothing new.
            throw new ApiException(HttpStatus.FORBIDDEN, "email_not_verified", "Please confirm your email address "
                    + "first. Use the link we emailed you when you signed up, or send a new one.");
        }
        return respond(user.get());
    }

    /** Confirms the address behind a confirmation link and signs the user in. */
    @Transactional
    public AuthResponse verifyEmail(String token) {
        EmailToken redeemed = tokens.redeem(token, Purpose.VERIFY_EMAIL);
        User user = users.findById(redeemed.getUserId()).orElseThrow(() -> ApiException.notFound("Account"));
        user.markEmailVerified();
        return respond(users.save(user));
    }

    /** Sends a fresh confirmation link. Silent when there is nothing to do, so it cannot reveal who has an account. */
    @Transactional
    public void resendVerification(String email) {
        users.findByEmailIgnoreCase(normalize(email))
                .filter(user -> !user.isEmailVerified())
                .filter(user -> !tokens.recentlyIssued(user, Purpose.VERIFY_EMAIL, settings.emailResendCooldown()))
                .ifPresent(user -> mailer.sendVerification(user,
                        tokens.issue(user, Purpose.VERIFY_EMAIL, settings.emailVerificationTtl())));
    }

    /** Emails a password-reset link. Silent for unknown addresses, so it cannot reveal who has an account. */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(normalize(email))
                .filter(user -> !tokens.recentlyIssued(user, Purpose.RESET_PASSWORD, settings.emailResendCooldown()))
                .ifPresent(user -> mailer.sendPasswordReset(user,
                        tokens.issue(user, Purpose.RESET_PASSWORD, settings.passwordResetTtl())));
    }

    /** Sets a new password from a reset link and signs the user in. */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        EmailToken redeemed = tokens.redeem(request.token(), Purpose.RESET_PASSWORD);
        User user = users.findById(redeemed.getUserId()).orElseThrow(() -> ApiException.notFound("Account"));
        user.changePassword(passwordEncoder.encode(request.password()));
        user.markEmailVerified(); // the link proves they own the address
        tokens.revokeUnused(user, Purpose.RESET_PASSWORD);
        tokens.revokeUnused(user, Purpose.VERIFY_EMAIL);
        return respond(users.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(long userId) {
        return users.findById(userId).map(AuthService::toResponse).orElseThrow(() -> ApiException.notFound("User"));
    }

    private AuthResponse respond(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getCountryCode(), user.getPhoneNumber(), user.isEmailVerified(), user.getCreatedAt());
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Trims and collapses inner whitespace: "  mary   jane " becomes "mary jane". */
    private static String tidy(String name) {
        return name.strip().replaceAll("\\s+", " ");
    }
}
