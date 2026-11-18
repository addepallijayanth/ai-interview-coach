package com.example.coach.config;

import com.example.coach.security.RateLimitInterceptor;
import com.example.coach.security.RateLimitInterceptor.Policy;
import com.example.coach.security.RateLimiter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires Bucket4j limits onto the endpoints that cost money (AI) or are brute-force targets (auth). */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimiter limiter;
    private final CoachProperties properties;

    public RateLimitConfig(RateLimiter limiter, CoachProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(Policy.AUTH, limiter, properties.rateLimit()))
                .addPathPatterns(SecurityConfig.PUBLIC_AUTH_ENDPOINTS);
        registry.addInterceptor(new RateLimitInterceptor(Policy.AI, limiter, properties.rateLimit()))
                .addPathPatterns("/api/analyses", "/api/interviews", "/api/interviews/*/answers",
                        "/api/interviews/*/end");
    }
}
