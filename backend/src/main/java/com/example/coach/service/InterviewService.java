package com.example.coach.service;

import com.example.coach.ai.AiExecutor;
import com.example.coach.ai.ClaudeClient;
import com.example.coach.ai.agent.AgentTranscript;
import com.example.coach.ai.agent.InterviewAgent;
import com.example.coach.ai.agent.InterviewHooks;
import com.example.coach.ai.agent.InterviewLevel;
import com.example.coach.ai.agent.InterviewPlan;
import com.example.coach.ai.agent.InterviewState;
import com.example.coach.ai.agent.QuestionCategory;
import com.example.coach.ai.model.AnalysisResult;
import com.example.coach.ai.model.MatchResult;
import com.example.coach.ai.model.ResumeProfile;
import com.example.coach.ai.tools.ScoreAnswerInput;
import com.example.coach.ai.tools.ToolContext;
import com.example.coach.config.CoachProperties;
import com.example.coach.dto.InterviewDtos.AnswerFeedback;
import com.example.coach.dto.InterviewDtos.EndedEvent;
import com.example.coach.dto.InterviewDtos.ErrorEvent;
import com.example.coach.dto.InterviewDtos.FeedbackEvent;
import com.example.coach.dto.InterviewDtos.InterviewResponse;
import com.example.coach.dto.InterviewDtos.InterviewSummary;
import com.example.coach.dto.InterviewDtos.QuestionEvent;
import com.example.coach.dto.InterviewDtos.StartInterviewRequest;
import com.example.coach.dto.InterviewDtos.StatusEvent;
import com.example.coach.dto.InterviewDtos.TokenEvent;
import com.example.coach.dto.InterviewDtos.TurnResponse;
import com.example.coach.exception.AiException;
import com.example.coach.exception.ApiException;
import com.example.coach.model.Analysis;
import com.example.coach.model.InterviewSession;
import com.example.coach.model.InterviewTurn;
import com.example.coach.model.JobDescription;
import com.example.coach.model.Resume;
import com.example.coach.repository.AnalysisRepository;
import com.example.coach.repository.InterviewSessionRepository;
import com.example.coach.repository.InterviewTurnRepository;
import com.example.coach.repository.JobDescriptionRepository;
import com.example.coach.repository.ResumeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Interview lifecycle. The browser submits an answer (POST), then opens the event stream (GET), which advances
 * the agent until it needs the candidate again. The next step is always derived from what is stored, so a
 * reload, a dropped connection or a server restart simply resumes where the interview left off.
 *
 * <p>Runs are serialised per session with an in-memory guard (fine for a single instance; use a database lock
 * or a lease column when scaling out).
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final AnalysisRepository analyses;
    private final ResumeRepository resumes;
    private final JobDescriptionRepository jobDescriptions;
    private final AnalysisService analysisService;
    private final InterviewMapper mapper;
    private final InterviewAgent agent;
    private final ClaudeClient claude;
    private final AiExecutor executor;
    private final TransactionTemplate tx;
    private final CoachProperties.Interview settings;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public InterviewService(InterviewSessionRepository sessions, InterviewTurnRepository turns,
            AnalysisRepository analyses, ResumeRepository resumes, JobDescriptionRepository jobDescriptions,
            AnalysisService analysisService, InterviewMapper mapper, InterviewAgent agent, ClaudeClient claude,
            AiExecutor executor, PlatformTransactionManager transactionManager, CoachProperties properties) {
        this.sessions = sessions;
        this.turns = turns;
        this.analyses = analyses;
        this.resumes = resumes;
        this.jobDescriptions = jobDescriptions;
        this.analysisService = analysisService;
        this.mapper = mapper;
        this.agent = agent;
        this.claude = claude;
        this.executor = executor;
        this.tx = new TransactionTemplate(transactionManager);
        this.settings = properties.interview();
    }

    // ------------------------------------------------------------------ queries and commands

    public InterviewResponse start(long userId, StartInterviewRequest request) {
        Analysis analysis = analyses.findByIdAndUserId(request.analysisId(), userId)
                .orElseThrow(() -> ApiException.notFound("Analysis"));
        if (!claude.isConfigured()) {
            throw AiException.notConfigured();
        }
        InterviewLevel level = request.level() == null ? InterviewLevel.STANDARD : request.level();
        List<QuestionCategory> plan = InterviewPlan.build(level, level.questions(settings));
        InterviewSession session = sessions.save(new InterviewSession(userId, analysis.getId(), level, plan.size(),
                mapper.toJson(plan)));
        return toResponse(session, List.of(), analysisService.result(analysis));
    }

    public InterviewResponse get(long userId, long sessionId) {
        InterviewSession session = owned(userId, sessionId);
        return toResponse(session, turns.findBySessionIdOrderByQuestionNoAsc(sessionId), resultOf(session));
    }

    public List<InterviewSummary> list(long userId) {
        List<InterviewSession> recent = sessions.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        if (recent.isEmpty()) {
            return List.of();
        }
        Map<Long, List<InterviewTurn>> turnsBySession = turns
                .findBySessionIdIn(recent.stream().map(InterviewSession::getId).toList()).stream()
                .collect(Collectors.groupingBy(InterviewTurn::getSessionId));
        Map<Long, Analysis> analysesById = analyses
                .findAllById(recent.stream().map(InterviewSession::getAnalysisId).distinct().toList()).stream()
                .collect(Collectors.toMap(Analysis::getId, Function.identity()));
        return recent.stream().map(session -> {
            Analysis analysis = analysesById.get(session.getAnalysisId());
            String title = analysis == null ? "Interview" : analysisService.result(analysis).job().title();
            return new InterviewSummary(session.getId(), session.getAnalysisId(), session.getStatus().name(),
                    session.getLevel().name(), session.getLevel().label(), title, session.getQuestionTarget(),
                    InterviewMapper.scoredCount(turnsBySession.getOrDefault(session.getId(), List.of())),
                    session.getOverallScore(), session.getCreatedAt());
        }).toList();
    }

    public TurnResponse submitAnswer(long userId, long sessionId, String answer) {
        InterviewSession session = owned(userId, sessionId);
        if (!session.isActive()) {
            throw ApiException.conflict("This interview has already ended.");
        }
        if (running.contains(sessionId)) {
            throw ApiException.conflict("The interviewer is still working. Please wait a moment.");
        }
        String text = answer == null ? "" : answer.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Write an answer first.");
        }
        if (text.length() > settings.maxAnswerChars()) {
            throw ApiException.badRequest("Answers can be at most " + settings.maxAnswerChars() + " characters.");
        }
        InterviewTurn turn = turns.findFirstBySessionIdOrderByQuestionNoDesc(sessionId)
                .filter(open -> !open.isAnswered())
                .orElseThrow(() -> ApiException.conflict("There is no open question to answer right now."));
        turn.recordAnswer(text);
        return mapper.toTurn(turns.save(turn));
    }

    public InterviewResponse requestEnd(long userId, long sessionId) {
        InterviewSession session = owned(userId, sessionId);
        if (session.isActive()) {
            if (running.contains(sessionId)) {
                throw ApiException.conflict("The interviewer is still working. Please wait a moment.");
            }
            List<InterviewTurn> all = turns.findBySessionIdOrderByQuestionNoAsc(sessionId);
            InterviewTurn last = all.isEmpty() ? null : all.getLast();
            if (last != null && last.isAnswered() && !last.isScored()) {
                throw ApiException.conflict("Wait for feedback on your last answer, then end the interview.");
            }
            if (InterviewMapper.scoredCount(all) == 0) {
                session.end("The interview was ended before any answers were given.", null); // nothing to evaluate
            } else {
                session.setEndRequested(true); // the stream asks the agent for a closing summary
            }
            session = sessions.save(session);
        }
        return toResponse(session, turns.findBySessionIdOrderByQuestionNoAsc(sessionId), resultOf(session));
    }

    // ------------------------------------------------------------------ streaming agent runs

    public SseEmitter stream(long userId, long sessionId) {
        owned(userId, sessionId); // 404 before the stream opens
        if (!running.add(sessionId)) {
            throw ApiException.conflict("This interview is already open in another tab.");
        }
        SseEmitter emitter = new SseEmitter(settings.streamTimeout().toMillis());
        SseSink sink = new SseSink(emitter);
        try {
            executor.execute(() -> {
                try {
                    advance(userId, sessionId, sink);
                } finally {
                    running.remove(sessionId);
                    sink.send("done", Map.of());
                    sink.complete();
                }
            });
        } catch (RuntimeException e) {
            running.remove(sessionId);
            throw e;
        }
        return emitter;
    }

    private void advance(long userId, long sessionId, SseSink sink) {
        boolean scoringAnswer = false;
        try {
            SessionData data = load(sessionId);
            InterviewSession session = data.session();
            if (!session.isActive()) {
                sink.send("ended", new EndedEvent(session.getOverallScore(), session.getSummary(),
                        InterviewMapper.scoredCount(data.turns())));
                return;
            }

            InterviewTurn last = data.turns().isEmpty() ? null : data.turns().getLast();
            Integer awaitingScore = last != null && last.isAnswered() && !last.isScored() ? last.getQuestionNo() : null;
            List<QuestionCategory> plan = mapper.plan(session);
            InterviewState state = new InterviewState(plan, data.turns().size(), awaitingScore,
                    session.isEndRequested());
            AgentTranscript transcript = AgentTranscript.fromJson(session.getTranscriptJson());

            // Nothing for the agent to do: the candidate still has to answer. Replay the open question.
            if (last != null && !last.isAnswered() && !session.isEndRequested() && !transcript.isEmpty()
                    && !transcript.awaitingModel()) {
                QuestionCategory category = last.getCategory() == null
                        ? state.categoryOf(last.getQuestionNo()) : last.getCategory();
                sink.send("question", new QuestionEvent(last.getQuestionNo(), last.getQuestion(),
                        state.questionTarget(), category.name(), category.label()));
                return;
            }

            RunHooks hooks = new RunHooks(sessionId, sink, state);
            InterviewAgent.Run run = new InterviewAgent.Run(userId, sessionId,
                    agent.systemPrompt(session.getLevel(), plan, data.role(), data.resumeText(), data.jobText()),
                    transcript,
                    new ToolContext(state, hooks, data.candidateProfileJson(), data.jobRequirementsJson()));

            InterviewAgent.Outcome outcome;
            if (transcript.isEmpty()) {
                outcome = agent.start(run);
            } else if (transcript.awaitingModel()) {
                outcome = agent.resume(run);
            } else if (session.isEndRequested() && awaitingScore == null) {
                outcome = agent.endEarly(run);
            } else if (last != null && last.isAnswered()) {
                scoringAnswer = awaitingScore != null;
                outcome = agent.answer(run, last.getQuestionNo(), last.getAnswer());
            } else {
                outcome = agent.endEarly(run); // unexpected state: close cleanly rather than loop
            }

            if (outcome == InterviewAgent.Outcome.STEP_LIMIT) {
                if (state.complete() || state.endRequested()) {
                    hooks.ended("The interview is complete. Review the feedback on each answer below.");
                } else {
                    sink.send("error", new ErrorEvent("agent_stuck",
                            "The interviewer lost track of the conversation. Please try again.", true));
                }
            }
        } catch (AiException e) {
            if (scoringAnswer && e.getKind() == AiException.Kind.REFUSED) {
                reopenLastAnswer(sessionId);
            }
            sink.send("error", new ErrorEvent(e.getCode(), e.getMessage(), retryable(e)));
        } catch (RuntimeException e) {
            log.error("Interview {} run failed", sessionId, e);
            sink.send("error", new ErrorEvent("internal_error", "Something went wrong. Please try again.", true));
        }
    }

    /** Persists agent results and forwards them to the browser. */
    private final class RunHooks implements InterviewHooks {

        private final long sessionId;
        private final SseSink sink;
        private final InterviewState state;

        RunHooks(long sessionId, SseSink sink, InterviewState state) {
            this.sessionId = sessionId;
            this.sink = sink;
            this.state = state;
        }

        @Override
        public void status(String message) {
            sink.send("status", new StatusEvent(message));
        }

        @Override
        public void token(String text) {
            sink.send("token", new TokenEvent(text));
        }

        @Override
        public void draftReset() {
            sink.send("draft_reset", Map.of());
        }

        @Override
        public void scored(ScoreAnswerInput score) {
            tx.executeWithoutResult(status -> {
                InterviewTurn turn = turns.findBySessionIdAndQuestionNo(sessionId, score.questionNo()).orElseThrow();
                turn.recordScore(score.score(), mapper.toJson(
                        new AnswerFeedback(score.strengths(), score.weaknesses(), score.modelAnswer())));
                turns.save(turn);
            });
            sink.send("feedback", new FeedbackEvent(score.questionNo(), score.score(), score.strengths(),
                    score.weaknesses(), score.modelAnswer()));
        }

        @Override
        public void question(int questionNo, String question, AgentTranscript transcript) {
            QuestionCategory category = state.categoryOf(questionNo);
            tx.executeWithoutResult(status -> {
                turns.save(new InterviewTurn(sessionId, questionNo, category, question));
                saveTranscript(transcript);
            });
            sink.send("question", new QuestionEvent(questionNo, question, state.questionTarget(), category.name(),
                    category.label()));
        }

        @Override
        public void ended(String summary) {
            EndedEvent event = tx.execute(status -> {
                InterviewSession session = sessions.findById(sessionId).orElseThrow();
                List<InterviewTurn> all = turns.findBySessionIdOrderByQuestionNoAsc(sessionId);
                session.end(summary, InterviewMapper.overallScore(all));
                sessions.save(session);
                return new EndedEvent(session.getOverallScore(), summary, InterviewMapper.scoredCount(all));
            });
            sink.send("ended", event);
        }

        @Override
        public void checkpoint(AgentTranscript transcript) {
            tx.executeWithoutResult(status -> saveTranscript(transcript));
        }

        private void saveTranscript(AgentTranscript transcript) {
            InterviewSession session = sessions.findById(sessionId).orElseThrow();
            session.setTranscriptJson(transcript.toJson());
            sessions.save(session);
        }
    }

    // ------------------------------------------------------------------ helpers

    private record SessionData(InterviewSession session, List<InterviewTurn> turns, String role, String resumeText,
            String jobText, String candidateProfileJson, String jobRequirementsJson) {}

    private SessionData load(long sessionId) {
        return tx.execute(status -> {
            InterviewSession session = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Interview"));
            Analysis analysis = analyses.findById(session.getAnalysisId()).orElseThrow();
            AnalysisResult result = analysisService.result(analysis);
            Resume resume = resumes.findById(analysis.getResumeId()).orElseThrow();
            JobDescription jd = jobDescriptions.findById(analysis.getJdId()).orElseThrow();
            String company = result.job().company();
            String role = result.job().title() + (company == null || company.isBlank() ? "" : " at " + company);
            return new SessionData(session, turns.findBySessionIdOrderByQuestionNoAsc(sessionId), role,
                    resume.getRedactedText(), jd.getText(), mapper.toJson(profileForTool(result)),
                    mapper.toJson(result.job()));
        });
    }

    /** What get_candidate_profile returns: the extracted profile plus the match analysis (gaps first). */
    private static Map<String, Object> profileForTool(AnalysisResult result) {
        ResumeProfile candidate = result.candidate();
        MatchResult match = result.match();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("skills", candidate.skills());
        profile.put("yearsExperience", candidate.yearsExperience());
        profile.put("roles", candidate.roles());
        profile.put("seniority", candidate.seniority());
        profile.put("matchScore", match.matchScore());
        profile.put("skillGaps", match.missingSkills());
        profile.put("matchedSkills", match.matchedSkills());
        profile.put("strengths", match.strengths());
        return profile;
    }

    private void reopenLastAnswer(long sessionId) {
        tx.executeWithoutResult(status -> turns.findFirstBySessionIdOrderByQuestionNoDesc(sessionId)
                .filter(turn -> turn.isAnswered() && !turn.isScored())
                .ifPresent(turn -> {
                    turn.clearAnswer();
                    turns.save(turn);
                }));
    }

    private static boolean retryable(AiException e) {
        return switch (e.getKind()) {
            case BUSY, UNAVAILABLE, BAD_OUTPUT -> true;
            case NOT_CONFIGURED, BUDGET_EXHAUSTED, REFUSED -> false;
        };
    }

    private InterviewSession owned(long userId, long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId).orElseThrow(() -> ApiException.notFound("Interview"));
    }

    private AnalysisResult resultOf(InterviewSession session) {
        return analysisService.result(analyses.findById(session.getAnalysisId()).orElseThrow());
    }

    private InterviewResponse toResponse(InterviewSession session, List<InterviewTurn> all, AnalysisResult result) {
        InterviewTurn last = all.isEmpty() ? null : all.getLast();
        boolean awaitingFeedback = session.isActive() && last != null && last.isAnswered() && !last.isScored();
        String company = result.job().company();
        return new InterviewResponse(session.getId(), session.getAnalysisId(), session.getStatus().name(),
                session.getLevel().name(), session.getLevel().label(), session.getQuestionTarget(),
                InterviewMapper.scoredCount(all), session.getOverallScore(),
                session.getSummary(), result.job().title(), company == null || company.isBlank() ? null : company,
                awaitingFeedback, all.stream().map(mapper::toTurn).toList(), session.getCreatedAt(),
                session.getEndedAt());
    }
}
