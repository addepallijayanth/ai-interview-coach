package com.example.coach.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Base class for errors that map to a specific HTTP status and a stable, machine-readable code. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    /** Extra members of the problem+json body, e.g. {@code errors} for field validation. */
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /** Adds a member to the error body. */
    public ApiException with(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", message);
    }

    public static ApiException unsupportedFile(String message) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_file", message);
    }

    /** A field-level validation failure found by a service, in the same shape as Bean Validation errors. */
    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.")
                .with("errors", Map.of(field, message));
    }
}
