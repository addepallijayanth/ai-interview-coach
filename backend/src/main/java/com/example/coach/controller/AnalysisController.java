package com.example.coach.controller;

import com.example.coach.dto.AnalysisDtos.AnalysisRequest;
import com.example.coach.dto.AnalysisDtos.AnalysisResponse;
import com.example.coach.dto.AnalysisDtos.AnalysisSummary;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")
@Tag(name = "Analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Match a resume against a job description: score, skill gaps and bullet rewrites")
    public AnalysisResponse create(@CurrentUserId Long userId, @Valid @RequestBody AnalysisRequest request) {
        return analysisService.create(userId, request);
    }

    @GetMapping
    @Operation(summary = "Your recent analyses")
    public List<AnalysisSummary> list(@CurrentUserId Long userId) {
        return analysisService.list(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One analysis")
    public AnalysisResponse get(@CurrentUserId Long userId, @PathVariable long id) {
        return analysisService.get(userId, id);
    }
}
