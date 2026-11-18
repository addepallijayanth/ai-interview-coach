package com.example.coach.ai.agent;

import java.util.List;

/** Progress of one interview, shared by the agent loop and its tools during a run. Built from the database. */
public final class InterviewState {

    private final List<QuestionCategory> plan;
    private int questionsAsked;
    private Integer awaitingScore;
    private boolean endRequested;
    private boolean ended;

    /** {@code plan} holds the category of every planned question, in order (see {@link InterviewPlan}). */
    public InterviewState(List<QuestionCategory> plan, int questionsAsked, Integer awaitingScore,
            boolean endRequested) {
        if (plan.isEmpty()) {
            throw new IllegalArgumentException("An interview needs at least one planned question");
        }
        this.plan = List.copyOf(plan);
        this.questionsAsked = questionsAsked;
        this.awaitingScore = awaitingScore;
        this.endRequested = endRequested;
    }

    public List<QuestionCategory> plan() {
        return plan;
    }

    public int questionTarget() {
        return plan.size();
    }

    /** Planned category of question {@code questionNo} (1-based). */
    public QuestionCategory categoryOf(int questionNo) {
        return plan.get(Math.clamp(questionNo, 1, plan.size()) - 1);
    }

    /** Category of the question the interviewer should ask next. */
    public QuestionCategory nextCategory() {
        return categoryOf(questionsAsked + 1);
    }

    public int questionsAsked() {
        return questionsAsked;
    }

    /** Number of the question whose answer has been submitted but not scored yet, or {@code null}. */
    public Integer awaitingScore() {
        return awaitingScore;
    }

    public boolean endRequested() {
        return endRequested;
    }

    public boolean ended() {
        return ended;
    }

    /** Every planned question has been asked and scored. */
    public boolean complete() {
        return questionsAsked >= plan.size() && awaitingScore == null;
    }

    void awaitScoreFor(int questionNo) {
        awaitingScore = questionNo;
    }

    void questionAsked() {
        questionsAsked++;
    }

    void requestEnd() {
        endRequested = true;
    }

    public void scoreRecorded() {
        awaitingScore = null;
    }

    public void markEnded() {
        ended = true;
    }
}
