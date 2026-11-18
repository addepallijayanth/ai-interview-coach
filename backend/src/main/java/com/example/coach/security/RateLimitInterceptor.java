package com.example.coach.security;

import com.example.coach.config.CoachProperties;
import com.example.coach.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Two policies: AUTH throttles sign-in/registration per client IP (brute-force protection) and AI caps the
 * number of AI-backed requests each user can make per day (cost protection for the public demo).
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    public enum Policy { AUTH, AI }

    public static final String AI_KEY_PREFIX = "ai:";

    private final Policy policy;
    private final RateLimiter limiter;
    private final CoachProperties.RateLimit limits;

    public RateLimitInterceptor(Policy policy, RateLimiter limiter, CoachProperties.RateLimit limits) {
        this.policy = policy;
        this.limiter = limiter;
        this.limits = limits;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        RateLimiter.Probe probe;
        String message;
        if (policy == Policy.AUTH) {
            probe = limiter.tryConsume("auth:" + request.getRemoteAddr(), limits.authRequestsPerMinute(),
                    Duration.ofMinutes(1));
            message = "Too many attempts. Please wait a minute and try again.";
        } else {
            Long userId = CurrentUserIdArgumentResolver.currentUserId();
            if (userId == null) {
                return true; // unauthenticated requests are rejected by Spring Security anyway
            }
            probe = limiter.tryConsume(AI_KEY_PREFIX + userId, limits.aiRequestsPerDay(), Duration.ofDays(1));
            message = "You have used today's " + limits.aiRequestsPerDay() + " AI requests. Please come back tomorrow.";
        }
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.remaining()));
        if (!probe.consumed()) {
            throw new RateLimitExceededException(message, Math.max(1, probe.retryAfter().toSeconds()));
        }
        return true;
    }
}
