package com.example.coach.controller;

import com.example.coach.dto.ReportDtos.ReportResponse;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Report data for a finished interview (generates the study plan on first view)")
    public ReportResponse report(@CurrentUserId Long userId, @PathVariable long sessionId) {
        return reportService.report(userId, sessionId);
    }

    @GetMapping(path = "/{sessionId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the report as a PDF")
    public ResponseEntity<byte[]> pdf(@CurrentUserId Long userId, @PathVariable long sessionId) {
        byte[] pdf = reportService.pdf(userId, sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("interview-report-" + sessionId + ".pdf").build().toString())
                .body(pdf);
    }
}
