package com.example.coach.service;

import com.example.coach.ai.AiExecutor;
import com.example.coach.ai.AnalysisPipeline;
import com.example.coach.ai.CostCalculator;
import com.example.coach.ai.Structured;
import com.example.coach.ai.TokenUsage;
import com.example.coach.ai.model.AnalysisResult;
import com.example.coach.ai.model.JobRequirements;
import com.example.coach.ai.model.MatchResult;
import com.example.coach.ai.model.ResumeProfile;
import com.example.coach.dto.AnalysisDtos.AnalysisRequest;
import com.example.coach.dto.AnalysisDtos.AnalysisResponse;
import com.example.coach.dto.AnalysisDtos.AnalysisSummary;
import com.example.coach.exception.ApiException;
import com.example.coach.model.Analysis;
import com.example.coach.model.JobDescription;
import com.example.coach.model.Resume;
import com.example.coach.repository.AnalysisRepository;
import com.example.coach.repository.JobDescriptionRepository;
import com.example.coach.repository.ResumeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Runs the resume-vs-job analysis and stores it. Extraction results are cached on the resume and job rows, so
 * analysing the same resume against a new job only pays for one cheap extraction plus the comparison.
 * No database transaction is held open while the AI calls run.
 */
@Service
public class AnalysisService {

    private final AnalysisPipeline pipeline;
    private final AiExecutor executor;
    private final CostCalculator costCalculator;
    private final ResumeRepository resumes;
    private final JobDescriptionRepository jobDescriptions;
    private final AnalysisRepository analyses;
    private final ObjectMapper objectMapper;

    public AnalysisService(AnalysisPipeline pipeline, AiExecutor executor, CostCalculator costCalculator,
            ResumeRepository resumes, JobDescriptionRepository jobDescriptions, AnalysisRepository analyses,
            ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.executor = executor;
        this.costCalculator = costCalculator;
        this.resumes = resumes;
        this.jobDescriptions = jobDescriptions;
        this.analyses = analyses;
        this.objectMapper = objectMapper;
    }

    public AnalysisResponse create(long userId, AnalysisRequest request) {
        Resume resume = resumes.findByIdAndUserId(request.resumeId(), userId)
                .orElseThrow(() -> ApiException.notFound("Resume"));
        JobDescription jd = jobDescriptions.findByIdAndUserId(request.jdId(), userId)
                .orElseThrow(() -> ApiException.notFound("Job description"));

        // 1. Extraction (cheap model), cached per document and run in parallel when both are needed.
        CompletableFuture<Structured<ResumeProfile>> candidateFuture = resume.getProfileJson() != null
                ? CompletableFuture.completedFuture(Structured.cached(read(resume.getProfileJson(), ResumeProfile.class)))
                : executor.supply(() -> pipeline.extractResume(userId, resume.getRedactedText()));
        CompletableFuture<Structured<JobRequirements>> jobFuture = jd.getRequirementsJson() != null
                ? CompletableFuture.completedFuture(Structured.cached(read(jd.getRequirementsJson(), JobRequirements.class)))
                : executor.supply(() -> pipeline.extractJob(userId, jd.getText()));
        Structured<ResumeProfile> candidate = AnalysisPipeline.join(candidateFuture);
        Structured<JobRequirements> job = AnalysisPipeline.join(jobFuture);
        cacheExtractions(resume, jd, candidate, job);

        // 2. Comparison and rewrite suggestions (stronger model).
        Structured<MatchResult> match = pipeline.match(userId, candidate.value(), job.value(),
                resume.getRedactedText(), jd.getText());

        List<Structured<?>> calls = List.of(candidate, job, match);
        TokenUsage usage = calls.stream().map(Structured::usage).reduce(TokenUsage.ZERO, TokenUsage::plus);
        BigDecimal cost = calls.stream()
                .filter(call -> !call.fromCache())
                .map(call -> costCalculator.cost(call.model(), call.usage()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String models = calls.stream().map(Structured::model).filter(Objects::nonNull).distinct()
                .collect(Collectors.joining(" + "));

        AnalysisResult result = new AnalysisResult(candidate.value(), job.value(), match.value());
        Analysis analysis = analyses.save(new Analysis(userId, resume.getId(), jd.getId(), match.value().matchScore(),
                write(result), models.isEmpty() ? "cached" : models, Math.toIntExact(usage.totalInputTokens()),
                Math.toIntExact(usage.outputTokens()), cost));
        return toResponse(analysis, result);
    }

    public AnalysisResponse get(long userId, long analysisId) {
        Analysis analysis = analyses.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> ApiException.notFound("Analysis"));
        return toResponse(analysis, result(analysis));
    }

    public List<AnalysisSummary> list(long userId) {
        return analyses.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(analysis -> {
                    JobRequirements job = result(analysis).job();
                    return new AnalysisSummary(analysis.getId(), job.title(), emptyToNull(job.company()),
                            analysis.getMatchScore(), analysis.getCreatedAt());
                })
                .toList();
    }

    public AnalysisResult result(Analysis analysis) {
        return read(analysis.getResultJson(), AnalysisResult.class);
    }

    private void cacheExtractions(Resume resume, JobDescription jd, Structured<ResumeProfile> candidate,
            Structured<JobRequirements> job) {
        if (!candidate.fromCache()) {
            resume.setProfileJson(write(candidate.value()));
            resumes.save(resume);
        }
        if (!job.fromCache()) {
            jd.setRequirementsJson(write(job.value()));
            if (jd.getTitle() == null) {
                jd.setTitle(truncate(job.value().title()));
            }
            if (jd.getCompany() == null && !job.value().company().isBlank()) {
                jd.setCompany(truncate(job.value().company()));
            }
            jobDescriptions.save(jd);
        }
    }

    private AnalysisResponse toResponse(Analysis analysis, AnalysisResult result) {
        return new AnalysisResponse(analysis.getId(), analysis.getResumeId(), analysis.getJdId(), result.job().title(),
                emptyToNull(result.job().company()), analysis.getMatchScore(), result.match(), result.candidate(),
                result.job(), analysis.getModelUsed(), analysis.getTokensIn(), analysis.getTokensOut(),
                analysis.getCostUsd(), analysis.getCreatedAt());
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored " + type.getSimpleName() + " could not be read", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value) {
        return value == null ? null : value.length() > 200 ? value.substring(0, 200) : value;
    }

    static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
