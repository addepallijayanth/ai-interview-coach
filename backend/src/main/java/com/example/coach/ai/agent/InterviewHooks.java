package com.example.coach.ai.agent;

import com.example.coach.ai.tools.ScoreAnswerInput;

/**
 * Side effects of an agent run: streaming progress to the browser and persisting results. Implemented by
 * {@code InterviewService}, which keeps the agent itself free of web and database code.
 */
public interface InterviewHooks {

    /** Short progress line for the UI, e.g. "Scoring your answer...". */
    void status(String message);

    /** A streamed text delta. Treat as a draft until {@link #question} confirms it. */
    void token(String text);

    /** The streamed draft was not a question (it preceded a tool call or was discarded): clear it. */
    void draftReset();

    /** The score_answer tool recorded feedback for a question. */
    void scored(ScoreAnswerInput score);

    /** The agent asked a new question. Persist it together with the transcript. */
    void question(int questionNo, String question, AgentTranscript transcript);

    /** The end_interview tool closed the session. */
    void ended(String summary);

    /** Persist the transcript at a consistent point (after a full model step). */
    void checkpoint(AgentTranscript transcript);
}
