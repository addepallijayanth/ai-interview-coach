package com.example.coach.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Anthropic SDK client. The API key only ever comes from configuration (the ANTHROPIC_API_KEY
 * environment variable); without one the app still starts, but AI endpoints return 503.
 */
@Configuration
public class ClaudeConfig {

    private static final Logger log = LoggerFactory.getLogger(ClaudeConfig.class);

    @Bean
    AnthropicClient anthropicClient(CoachProperties properties) {
        CoachProperties.Ai ai = properties.ai();
        if (!ai.configured()) {
            log.warn("ANTHROPIC_API_KEY is not set: AI features are disabled until it is configured.");
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(ai.configured() ? ai.apiKey() : "not-configured")
                .baseUrl(ai.baseUrl())
                .timeout(ai.timeout())
                .maxRetries(ai.maxRetries()) // the SDK retries 408/409/429/5xx and connection errors with backoff
                .build();
    }
}
