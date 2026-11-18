package com.example.coach.ai;

/** Who a Claude call is made for, used for spend tracking. {@code sessionId} is set for interview-related calls. */
public record AiCall(AiTask task, Long userId, Long sessionId) {

    public static AiCall of(AiTask task, Long userId) {
        return new AiCall(task, userId, null);
    }
}
