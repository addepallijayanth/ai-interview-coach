package com.example.coach.service;

import com.example.coach.exception.ApiException;
import com.example.coach.model.EmailToken;
import com.example.coach.model.EmailToken.Purpose;
import com.example.coach.model.User;
import com.example.coach.repository.EmailTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems the single-use tokens behind email links. A token is 256 random bits; the database keeps only
 * its SHA-256 hash, so a leaked database cannot be used to confirm accounts or reset passwords.
 */
@Service
public class EmailTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository tokens;

    public EmailTokenService(EmailTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** Creates a new link token for {@code user}; older unused links of the same kind stop working. */
    @Transactional
    public String issue(User user, Purpose purpose, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.deleteUnused(user.getId(), purpose);
        tokens.save(new EmailToken(user.getId(), purpose, hash(token), Instant.now().plus(ttl)));
        return token;
    }

    /** True when a link of this kind was sent to the user less than {@code cooldown} ago. */
    @Transactional(readOnly = true)
    public boolean recentlyIssued(User user, Purpose purpose, Duration cooldown) {
        return tokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(latest -> latest.getCreatedAt().isAfter(Instant.now().minus(cooldown)))
                .isPresent();
    }

    /**
     * Marks the token as used and returns it, or explains why the link no longer works. Runs in the caller's
     * transaction, so the token is only consumed if the whole action succeeds.
     */
    @Transactional
    public EmailToken redeem(String token, Purpose purpose) {
        EmailToken stored = tokens.findByTokenHash(hash(token.strip()))
                .filter(found -> found.getPurpose() == purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_link",
                        "This link is not valid. It may have been replaced by a newer one, so use the latest "
                                + "email we sent or request a new link."));
        if (stored.isUsed()) {
            throw new ApiException(HttpStatus.GONE, "link_used", purpose == Purpose.VERIFY_EMAIL
                    ? "This link has already been used, so your email is confirmed. Sign in to continue."
                    : "This link has already been used. Request a new one if you still need to reset your password.");
        }
        if (stored.isExpired(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "link_expired", purpose == Purpose.VERIFY_EMAIL
                    ? "This confirmation link has expired. Request a new one below."
                    : "This password reset link has expired. Request a new one below.");
        }
        stored.markUsed();
        return tokens.save(stored);
    }

    /** Invalidates every unused link of this kind, e.g. other reset links once the password has been changed. */
    @Transactional
    public void revokeUnused(User user, Purpose purpose) {
        tokens.deleteUnused(user.getId(), purpose);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
