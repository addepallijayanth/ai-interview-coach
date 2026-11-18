package com.example.coach.service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Safe wrapper around {@link SseEmitter}. If the browser disconnects, sends become no-ops instead of exceptions,
 * so the agent run can still finish and persist its state (a page reload then replays the result).
 */
final class SseSink {

    private static final Logger log = LoggerFactory.getLogger(SseSink.class);

    private final SseEmitter emitter;
    private volatile boolean open = true;

    SseSink(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> open = false);
        emitter.onTimeout(() -> open = false);
        emitter.onError(error -> open = false);
    }

    /** Payloads are JSON objects, which keeps multi-line text safe inside SSE data lines. */
    synchronized void send(String event, Object data) {
        if (!open) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            open = false;
            log.debug("SSE client went away: {}", e.getMessage());
        }
    }

    synchronized void complete() {
        if (open) {
            open = false;
            try {
                emitter.complete();
            } catch (RuntimeException e) {
                log.debug("SSE completion failed: {}", e.getMessage());
            }
        }
    }
}
