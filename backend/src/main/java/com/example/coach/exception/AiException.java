package com.example.coach.exception;

import org.springframework.http.HttpStatus;

/** A failure in the AI layer, already translated into something safe to show the user. */
public class AiException extends ApiException {

    public enum Kind {
        NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "ai_not_configured"),
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ai_unavailable"),
        BUSY(HttpStatus.SERVICE_UNAVAILABLE, "ai_busy"),
        BUDGET_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "ai_budget_exhausted"),
        REFUSED(HttpStatus.UNPROCESSABLE_ENTITY, "ai_refused"),
        BAD_OUTPUT(HttpStatus.BAD_GATEWAY, "ai_bad_output");

        private final HttpStatus status;
        private final String code;

        Kind(HttpStatus status, String code) {
            this.status = status;
            this.code = code;
        }
    }

    private final Kind kind;

    public AiException(Kind kind, String message) {
        this(kind, message, null);
    }

    public AiException(Kind kind, String message, Throwable cause) {
        super(kind.status, kind.code, message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static AiException notConfigured() {
        return new AiException(Kind.NOT_CONFIGURED,
                "AI features are disabled because ANTHROPIC_API_KEY is not set on the server.");
    }

    public static AiException budgetExhausted() {
        return new AiException(Kind.BUDGET_EXHAUSTED,
                "This demo has used its AI budget for today. Please try again tomorrow.");
    }

    public static AiException refused() {
        return new AiException(Kind.REFUSED,
                "The AI declined to process this content. Try rephrasing or using different input.");
    }

    public static AiException badOutput(String detail) {
        return new AiException(Kind.BAD_OUTPUT, "The AI returned an unusable response: " + detail);
    }
}
