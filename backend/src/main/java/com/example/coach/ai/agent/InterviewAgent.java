package com.example.coach.ai.agent;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.example.coach.ai.AiCall;
import com.example.coach.ai.AiTask;
import com.example.coach.ai.ClaudeClient;
import com.example.coach.ai.ModelRouter;
import com.example.coach.ai.PromptTemplates;
import com.example.coach.ai.StructuredOutputService;
import com.example.coach.ai.tools.AgentTool;
import com.example.coach.ai.tools.ToolContext;
import com.example.coach.ai.tools.ToolRegistry;
import com.example.coach.ai.tools.ToolResult;
import com.example.coach.config.CoachProperties;
import com.example.coach.exception.AiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The mock interviewer: a tool-using agent loop on the Messages API.
 *
 * <p>Each run starts when the candidate does something (starts, answers, or ends the interview) and continues
 * until the agent needs the candidate again: it asks the next question or ends the interview. Claude decides
 * which tools to call; this class executes them, feeds the results back, and enforces the guard rails:
 * <ul>
 *   <li>a maximum number of model calls per run, so a confused model cannot loop or burn money;</li>
 *   <li>protocol checks (score before asking, stop when finished). A reply that breaks the protocol is dropped
 *       and the agent is steered with a mid-conversation system message. History is never edited;</li>
 *   <li>refusals and truncated replies stop the run without being saved.</li>
 * </ul>
 */
@Component
public class InterviewAgent {

    private static final Logger log = LoggerFactory.getLogger(InterviewAgent.class);

    static final String KICKOFF_MESSAGE = "Hi! I'm ready to start the mock interview.";
    static final String END_MESSAGE = "I'd like to end the interview here, please.";

    public enum Outcome { AWAITING_ANSWER, ENDED, STEP_LIMIT }

    /** Everything one run needs. {@code system} must be identical for every run of a session. */
    public record Run(Long userId, long sessionId, List<TextBlockParam> system, AgentTranscript transcript,
            ToolContext tools) {

        InterviewState state() {
            return tools.state();
        }

        InterviewHooks hooks() {
            return tools.hooks();
        }
    }

    private final ClaudeClient claude;
    private final ModelRouter router;
    private final ToolRegistry tools;
    private final PromptTemplates prompts;
    private final String rules;
    private final int maxSteps;

    public InterviewAgent(ClaudeClient claude, ModelRouter router, ToolRegistry tools, PromptTemplates prompts,
            CoachProperties properties) {
        this.claude = claude;
        this.router = router;
        this.tools = tools;
        this.prompts = prompts;
        this.rules = prompts.render("interviewer-system", Map.of("categories", QuestionCategory.promptGuide()));
        this.maxSteps = properties.interview().maxAgentSteps();
    }

    /**
     * Two cached system blocks: the interviewer rules (identical for every session, so one cache entry serves
     * all users) and this session's settings, question plan, resume and job description (reused on every turn).
     */
    public List<TextBlockParam> systemPrompt(InterviewLevel level, List<QuestionCategory> plan, String role,
            String resumeText, String jobText) {
        StringBuilder planLines = new StringBuilder();
        for (int i = 0; i < plan.size(); i++) {
            planLines.append(i == 0 ? "" : "\n").append(i + 1).append(": ").append(plan.get(i).label());
        }
        String context = prompts.render("interview-context", Map.of(
                "role", PromptTemplates.sanitize("interview_settings", role),
                "level", level.interviewerGuidance(),
                "questionTarget", String.valueOf(plan.size()),
                "plan", planLines.toString(),
                "resume", PromptTemplates.untrusted("resume", resumeText),
                "jobDescription", PromptTemplates.untrusted("job_description", jobText)));
        CacheControlEphemeral cache = CacheControlEphemeral.builder().build();
        return List.of(
                TextBlockParam.builder().text(rules).cacheControl(cache).build(),
                TextBlockParam.builder().text(context).cacheControl(cache).build());
    }

    public Outcome start(Run run) {
        run.transcript().add(MessageParam.ofUser(KICKOFF_MESSAGE));
        return loop(run);
    }

    public Outcome answer(Run run, int questionNo, String answer) {
        run.state().awaitScoreFor(questionNo);
        run.transcript().add(MessageParam.ofUser("<candidate_answer question_no=\"" + questionNo + "\">\n"
                + PromptTemplates.sanitize("candidate_answer", answer) + "\n</candidate_answer>"));
        return loop(run);
    }

    public Outcome endEarly(Run run) {
        run.state().requestEnd();
        if (!run.transcript().awaitingModel()) {
            run.transcript().add(MessageParam.ofUser(END_MESSAGE));
        }
        if (!run.transcript().endsWithSystemMessage()) {
            run.transcript().add(MessageParam.ofSystem(endInstruction(run.state())));
        }
        return loop(run);
    }

    /** Continues a run that stopped part-way (for example after a network error) from the saved transcript. */
    public Outcome resume(Run run) {
        if (run.state().endRequested() && !run.transcript().endsWithSystemMessage()) {
            run.transcript().add(MessageParam.ofSystem(endInstruction(run.state())));
        }
        return loop(run);
    }

    private Outcome loop(Run run) {
        InterviewState state = run.state();
        InterviewHooks hooks = run.hooks();
        AiCall call = new AiCall(AiTask.INTERVIEW, run.userId(), run.sessionId());
        boolean retriedUnreadableReply = false;

        for (int step = 1; step <= maxSteps; step++) {
            Message message;
            try {
                message = claude.stream(params(run), call, hooks::token);
            } catch (AiException e) {
                hooks.draftReset();
                if (e.getKind() == AiException.Kind.BAD_OUTPUT && !retriedUnreadableReply) {
                    retriedUnreadableReply = true; // e.g. malformed streamed tool input: re-issue the request once
                    continue;
                }
                throw e;
            }

            StopReason stop = message.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stop)) {
                hooks.draftReset();
                throw AiException.refused();
            }
            if (StopReason.MAX_TOKENS.equals(stop)) {
                hooks.draftReset();
                throw AiException.badOutput("the interviewer's reply was cut off");
            }

            List<ToolUseBlock> toolUses = message.content().stream().flatMap(block -> block.toolUse().stream()).toList();
            if (!toolUses.isEmpty()) {
                hooks.draftReset(); // any streamed text was a preamble to the tool call, not a question
                run.transcript().add(message.toParam());
                run.transcript().add(MessageParam.ofUserOfContentBlockParams(executeTools(toolUses, run.tools())));
                hooks.checkpoint(run.transcript());
                if (state.ended()) {
                    return Outcome.ENDED;
                }
                continue;
            }

            String reply = StructuredOutputService.textOf(message).strip();
            String correction = correctionFor(state, reply);
            if (correction != null) {
                log.info("Session {}: dropped an off-protocol reply ({})", run.sessionId(), correction);
                hooks.draftReset();
                if (!run.transcript().endsWithSystemMessage()) {
                    run.transcript().add(MessageParam.ofSystem(correction));
                }
                continue;
            }

            run.transcript().add(message.toParam());
            state.questionAsked();
            hooks.question(state.questionsAsked(), reply, run.transcript());
            return Outcome.AWAITING_ANSWER;
        }

        hooks.draftReset();
        log.warn("Session {}: agent stopped after the {}-step limit", run.sessionId(), maxSteps);
        return Outcome.STEP_LIMIT;
    }

    private MessageCreateParams params(Run run) {
        ModelRouter.ModelChoice choice = router.route(AiTask.INTERVIEW);
        return MessageCreateParams.builder()
                .model(choice.model())
                .maxTokens(choice.maxTokens())
                .systemOfTextBlockParams(run.system())
                .tools(tools.apiTools())
                .messages(run.transcript().messages())
                .outputConfig(OutputConfig.builder().effort(choice.effort()).build())
                // Automatic caching moves a breakpoint to the end of the growing conversation on every turn.
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();
    }

    /** Runs every tool call of one assistant turn and returns all results for a single user message. */
    private List<ContentBlockParam> executeTools(List<ToolUseBlock> toolUses, ToolContext context) {
        List<ContentBlockParam> results = new ArrayList<>();
        for (ToolUseBlock use : toolUses) {
            tools.find(use.name()).map(AgentTool::statusMessage).ifPresent(context.hooks()::status);
            ToolResult result;
            try {
                JsonNode input = use._input().convert(JsonNode.class);
                result = tools.execute(use.name(), input, context);
            } catch (RuntimeException e) {
                result = ToolResult.error("{\"INVALID_JSON\": " + ToolResult.quote(String.valueOf(use._input())) + "}");
            }
            results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(use.id())
                    .content(result.content())
                    .isError(result.error())
                    .build()));
        }
        return results;
    }

    /** Returns an instruction when a plain-text reply breaks the interview protocol, else {@code null}. */
    static String correctionFor(InterviewState state, String reply) {
        if (state.awaitingScore() != null) {
            return "Before anything else, call score_answer for question " + state.awaitingScore() + ".";
        }
        if (state.endRequested() || state.complete()) {
            return "The interview is over. Call end_interview now with your summary and do not ask another question.";
        }
        if (reply.isEmpty()) {
            return "Ask question " + (state.questionsAsked() + 1) + " now (category: "
                    + state.nextCategory().label() + ").";
        }
        return null;
    }

    private static String endInstruction(InterviewState state) {
        return "The candidate has ended the interview early, after " + state.questionsAsked() + " of "
                + state.questionTarget() + " planned questions. Call end_interview now. Base the summary only on "
                + "the answers they gave and do not ask another question.";
    }
}
