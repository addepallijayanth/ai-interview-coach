package com.example.coach.controller;

import com.example.coach.dto.DocumentDtos.JobDescriptionRequest;
import com.example.coach.dto.DocumentDtos.JobDescriptionResponse;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.JobDescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-descriptions")
@Tag(name = "Job descriptions")
public class JobDescriptionController {

    private final JobDescriptionService jobDescriptionService;

    public JobDescriptionController(JobDescriptionService jobDescriptionService) {
        this.jobDescriptionService = jobDescriptionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a pasted job description")
    public JobDescriptionResponse create(@CurrentUserId Long userId, @Valid @RequestBody JobDescriptionRequest request) {
        return jobDescriptionService.create(userId, request);
    }

    @GetMapping
    @Operation(summary = "Your recent job descriptions")
    public List<JobDescriptionResponse> list(@CurrentUserId Long userId) {
        return jobDescriptionService.list(userId);
    }
}
