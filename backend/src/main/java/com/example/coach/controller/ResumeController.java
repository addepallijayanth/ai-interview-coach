package com.example.coach.controller;

import com.example.coach.dto.DocumentDtos.ResumeResponse;
import com.example.coach.dto.DocumentDtos.ResumeSummary;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@Tag(name = "Resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a PDF or DOCX resume (max 2 MB); returns the PII-redacted text")
    public ResumeResponse upload(@CurrentUserId Long userId, @RequestPart("file") MultipartFile file) {
        return resumeService.upload(userId, file);
    }

    @GetMapping
    @Operation(summary = "Your recent resumes")
    public List<ResumeSummary> list(@CurrentUserId Long userId) {
        return resumeService.list(userId);
    }
}
