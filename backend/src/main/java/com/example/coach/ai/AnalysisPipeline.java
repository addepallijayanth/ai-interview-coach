package com.example.coach.ai;

import com.example.coach.ai.model.AnalysisResult;
import com.example.coach.ai.model.JobRequirements;
import com.example.coach.ai.model.MatchResult;
import com.example.coach.ai.model.ResumeProfile;
import com.example.coach.config.CoachProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;

/**
 * The resume-vs-job analysis, independent of the database so the eval suite can run it directly.
 *
 * <pre>
 * resume ──[Haiku] extract ──┐
 *                            ├──▶ [Sonnet] compare, score, rewrite bullets
 * job ─────[Haiku] extract ──┘
 * </pre>
 *
 * The two extractions are independent, so they run in parallel on virtual threads.
 */
@Component
public class AnalysisPipeline {

    public record FullAnalysis(Structured<ResumeProfile> candidate, Structured<JobRequirements> job,
            Structured<MatchResult> match) {

        public AnalysisResult result() {
            return new AnalysisResult(candidate.value(), job.value(), match.value());
        }

        public TokenUsage usage() {
            return candidate.usage().plus(job.usage()).plus(match.usage());
        }
    }

    private final StructuredOutputService structured;
    private final PromptTemplates prompts;
    private final ObjectMapper objectMapper;
    private final AiExecutor executor;
    private final int maxDocumentChars;

    public AnalysisPipeline(StructuredOutputService structured, PromptTemplates prompts, ObjectMapper objectMapper,
            AiExecutor executor, CoachProperties properties) {
        this.structured = structured;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.maxDocumentChars = properties.ai().maxDocumentChars();
    }

    public Structured<ResumeProfile> extractResume(Long userId, String resumeText) {
        return structured.generate(StructuredRequest.of(AiTask.EXTRACT_RESUME, userId,
                prompts.get("extract-resume"), PromptTemplates.untrusted("resume", clip(resumeText)),
                "resume-profile", ResumeProfile.class));
    }

    public Structured<JobRequirements> extractJob(Long userId, String jobText) {
        return structured.generate(StructuredRequest.of(AiTask.EXTRACT_JOB, userId,
                        prompts.get("extract-jd"), PromptTemplates.untrusted("job_description", clip(jobText)),
                        "job-requirements", JobRequirements.class)
                .withChecks(job -> job.requiredSkills().isEmpty()
                                ? List.of("requiredSkills is empty: list the skills the role requires")
                                : List.of(),
                        job -> job));
    }

    public Structured<MatchResult> match(Long userId, ResumeProfile candidate, JobRequirements job,
            String resumeText, String jobText) {
        String userPrompt = String.join("\n\n",
                PromptTemplates.untrusted("candidate_profile", toJson(candidate)),
                PromptTemplates.untrusted("job_requirements", toJson(job)),
                PromptTemplates.untrusted("resume", clip(resumeText)),
                PromptTemplates.untrusted("job_description", clip(jobText)));
        return structured.generate(StructuredRequest.of(AiTask.MATCH, userId, prompts.get("match-and-suggest"),
                        userPrompt, "match-result", MatchResult.class)
                .withChecks(result -> MatchChecks.problems(result, resumeText),
                        result -> MatchChecks.repair(result, resumeText)));
    }

    /** Full pipeline without caching: extraction calls in parallel, then the comparison. */
    public FullAnalysis analyze(Long userId, String resumeText, String jobText) {
        CompletableFuture<Structured<ResumeProfile>> candidate = executor.supply(() -> extractResume(userId, resumeText));
        CompletableFuture<Structured<JobRequirements>> job = executor.supply(() -> extractJob(userId, jobText));
        Structured<ResumeProfile> candidateResult = join(candidate);
        Structured<JobRequirements> jobResult = join(job);
        Structured<MatchResult> match = match(userId, candidateResult.value(), jobResult.value(), resumeText, jobText);
        return new FullAnalysis(candidateResult, jobResult, match);
    }

    /** Unwraps the CompletionException so callers see the original AiException. */
    public static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxDocumentChars ? text : text.substring(0, maxDocumentChars);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
