package com.example.coach.controller;

import com.example.coach.dto.InterviewDtos.AnswerRequest;
import com.example.coach.dto.InterviewDtos.InterviewResponse;
import com.example.coach.dto.InterviewDtos.InterviewSummary;
import com.example.coach.dto.InterviewDtos.StartInterviewRequest;
import com.example.coach.dto.InterviewDtos.TurnResponse;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/interviews")
@Tag(name = "Interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a mock interview for an analysis; open the stream to receive question 1")
    public InterviewResponse start(@CurrentUserId Long userId, @Valid @RequestBody StartInterviewRequest request) {
        return interviewService.start(userId, request);
    }

    @GetMapping
    @Operation(summary = "Your recent interviews")
    public List<InterviewSummary> list(@CurrentUserId Long userId) {
        return interviewService.list(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Interview state with all questions, answers and feedback")
    public InterviewResponse get(@CurrentUserId Long userId, @PathVariable long id) {
        return interviewService.get(userId, id);
    }

    @PostMapping("/{id}/answers")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit an answer to the open question; then open the stream for feedback")
    public TurnResponse answer(@CurrentUserId Long userId, @PathVariable long id,
            @Valid @RequestBody AnswerRequest request) {
        return interviewService.submitAnswer(userId, id, request.answer());
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "End the interview early; open the stream to receive the summary")
    public InterviewResponse end(@CurrentUserId Long userId, @PathVariable long id) {
        return interviewService.requestEnd(userId, id);
    }

    /**
     * Server-sent events: status, token, draft_reset, feedback, question, ended, error, done. The stream advances
     * the interviewer until it needs the candidate again, then closes.
     */
    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream of feedback and the next question")
    public SseEmitter stream(@CurrentUserId Long userId, @PathVariable long id) {
        return interviewService.stream(userId, id);
    }
}
