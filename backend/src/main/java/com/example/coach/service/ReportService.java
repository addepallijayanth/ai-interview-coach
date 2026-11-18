package com.example.coach.service;

import com.example.coach.ai.AiTask;
import com.example.coach.ai.PromptTemplates;
import com.example.coach.ai.StructuredOutputService;
import com.example.coach.ai.StructuredRequest;
import com.example.coach.ai.model.AnalysisResult;
import com.example.coach.ai.model.StudyPlan;
import com.example.coach.dto.InterviewDtos.AnswerFeedback;
import com.example.coach.dto.ReportDtos.ReportResponse;
import com.example.coach.exception.AiException;
import com.example.coach.exception.ApiException;
import com.example.coach.model.Analysis;
import com.example.coach.model.InterviewSession;
import com.example.coach.model.InterviewTurn;
import com.example.coach.repository.AnalysisRepository;
import com.example.coach.repository.InterviewSessionRepository;
import com.example.coach.repository.InterviewTurnRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Final report: match analysis + interview results + an AI "what to study next" plan. The plan is generated
 * once (cheap model) and cached on the session, so repeated downloads cost nothing.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final AnalysisRepository analyses;
    private final AnalysisService analysisService;
    private final InterviewMapper mapper;
    private final StructuredOutputService structured;
    private final PromptTemplates prompts;
    private final UsageService usage;
    private final PdfReportRenderer renderer;
    private final ObjectMapper objectMapper;

    public ReportService(InterviewSessionRepository sessions, InterviewTurnRepository turns,
            AnalysisRepository analyses, AnalysisService analysisService, InterviewMapper mapper,
            StructuredOutputService structured, PromptTemplates prompts, UsageService usage,
            PdfReportRenderer renderer, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.turns = turns;
        this.analyses = analyses;
        this.analysisService = analysisService;
        this.mapper = mapper;
        this.structured = structured;
        this.prompts = prompts;
        this.usage = usage;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    public ReportResponse report(long userId, long sessionId) {
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Interview"));
        if (session.isActive()) {
            throw ApiException.conflict("Finish or end the interview to see its report.");
        }
        Analysis analysis = analyses.findById(session.getAnalysisId()).orElseThrow();
        AnalysisResult result = analysisService.result(analysis);
        List<InterviewTurn> all = turns.findBySessionIdOrderByQuestionNoAsc(sessionId);
        StudyPlan plan = studyPlan(userId, session, result, all);
        BigDecimal aiCost = usage.sessionCost(sessionId).add(analysis.getCostUsd());
        String company = result.job().company();

        return new ReportResponse(session.getId(), result.job().title(),
                company == null || company.isBlank() ? null : company, session.getLevel().name(),
                session.getLevel().label(), analysis.getMatchScore(), result.match().matchedSkills(),
                result.match().missingSkills(), session.getOverallScore(), InterviewMapper.scoredCount(all),
                session.getQuestionTarget(), session.getSummary(),
                InterviewMapper.categoryScores(all.stream().filter(InterviewTurn::isAnswered).toList()),
                all.stream().filter(InterviewTurn::isAnswered).map(mapper::toTurn).toList(), plan, aiCost,
                session.getEndedAt());
    }

    public byte[] pdf(long userId, long sessionId) {
        return renderer.render(report(userId, sessionId));
    }

    private StudyPlan studyPlan(long userId, InterviewSession session, AnalysisResult result,
            List<InterviewTurn> all) {
        if (session.getStudyPlanJson() != null) {
            return read(session.getStudyPlanJson());
        }
        try {
            StudyPlan plan = structured.generate(StructuredRequest.of(AiTask.STUDY_PLAN, userId,
                            prompts.get("study-plan"), studyPlanInput(result, all), "study-plan", StudyPlan.class)
                    .forSession(session.getId())).value();
            session.setStudyPlanJson(mapper.toJson(plan));
            sessions.save(session);
            return plan;
        } catch (AiException e) {
            log.warn("Study plan unavailable for session {}: {}", session.getId(), e.getMessage());
            return null; // the rest of the report is still useful
        }
    }

    private String studyPlanInput(AnalysisResult result, List<InterviewTurn> all) {
        StringBuilder interview = new StringBuilder();
        for (InterviewTurn turn : all) {
            if (!turn.isScored()) {
                continue;
            }
            AnswerFeedback feedback = mapper.feedback(turn);
            interview.append("Q").append(turn.getQuestionNo())
                    .append(turn.getCategory() == null ? "" : " [" + turn.getCategory().label() + "]")
                    .append(" (score ").append(turn.getScore()).append("/10): ").append(turn.getQuestion()).append('\n')
                    .append("Weaknesses noted: ")
                    .append(feedback == null || feedback.weaknesses().isEmpty() ? "none" : String.join("; ", feedback.weaknesses()))
                    .append("\n\n");
        }
        String company = result.job().company();
        String role = result.job().title() + (company == null || company.isBlank() ? "" : " at " + company);
        return String.join("\n\n",
                PromptTemplates.untrusted("role", role),
                PromptTemplates.untrusted("required_skills", String.join(", ", result.job().requiredSkills())),
                PromptTemplates.untrusted("missing_skills", String.join(", ", result.match().missingSkills())),
                PromptTemplates.untrusted("interview", interview.isEmpty() ? "No answers were scored." : interview.toString()));
    }

    private StudyPlan read(String json) {
        try {
            return objectMapper.readValue(json, StudyPlan.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored study plan could not be read", e);
        }
    }
}
