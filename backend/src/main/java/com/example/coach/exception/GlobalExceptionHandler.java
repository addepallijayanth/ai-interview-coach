package com.example.coach.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

/** Renders every error as an RFC 7807 problem+json body with a stable {@code code} property. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("{}: {}", ex.getCode(), ex.getMessage(), ex.getCause());
        }
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitExceededException rateLimited) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimited.getRetryAfterSeconds()));
        }
        ProblemDetail body = problem(ex.getStatus(), ex.getCode(), ex.getMessage());
        ex.getProperties().forEach(body::setProperty);
        return ResponseEntity.status(ex.getStatus()).headers(headers).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "conflict", "The request conflicts with existing data."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletResponse response) {
        // A browser that closes an SSE stream (tab closed, page reloaded) surfaces here as an I/O error on an
        // already-committed response: there is nobody to answer and nothing went wrong on our side.
        if (response.isCommitted() || DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            log.debug("Client disconnected: {}", ex.getMessage());
            return null;
        }
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong on our side."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
