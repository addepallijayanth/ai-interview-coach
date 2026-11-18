package com.example.coach.ai.agent;

import static com.example.coach.ai.agent.QuestionCategory.BEHAVIOURAL;
import static com.example.coach.ai.agent.QuestionCategory.HR;
import static com.example.coach.ai.agent.QuestionCategory.PROBLEM_SOLVING;
import static com.example.coach.ai.agent.QuestionCategory.PROJECT;
import static com.example.coach.ai.agent.QuestionCategory.SITUATIONAL;
import static com.example.coach.ai.agent.QuestionCategory.SYSTEM_DESIGN;
import static com.example.coach.ai.agent.QuestionCategory.TECHNICAL;

import com.example.coach.config.CoachProperties;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Interview difficulty. Each level sets the question count (configurable, 10 / 20 / 30 by default), how demanding
 * the interviewer is, and the mix of question categories. The weights below are the mix at the default size;
 * other sizes are scaled proportionally by {@link InterviewPlan}.
 */
public enum InterviewLevel {

    EASY("Easy",
            "A warm-up round: fundamentals and common scenarios, with an encouraging interviewer.",
            "Easy. Test fundamentals and common, well-known scenarios. Keep each question to one concept, avoid trick "
                    + "questions and keep the tone friendly and encouraging. Suits students, freshers and a first "
                    + "practice round.",
            weights(TECHNICAL, 3, PROBLEM_SOLVING, 1, SYSTEM_DESIGN, 0, PROJECT, 1, BEHAVIOURAL, 2, SITUATIONAL, 1,
                    HR, 2)),
    STANDARD("Standard",
            "A typical full interview: applied questions and follow-ups on vague answers.",
            "Standard. Hold the bar of a typical interview for this role. Mix fundamentals with applied, realistic "
                    + "problems and expect concrete examples from real experience.",
            weights(TECHNICAL, 5, PROBLEM_SOLVING, 2, SYSTEM_DESIGN, 2, PROJECT, 3, BEHAVIOURAL, 3, SITUATIONAL, 2,
                    HR, 3)),
    PROFESSIONAL("Professional",
            "A demanding senior-level loop: depth, scale, trade-offs and leadership.",
            "Professional. Hold a demanding senior-level bar. Probe for depth, scale, trade-offs, production "
                    + "experience and leadership; challenge vague claims and ask for specifics and numbers.",
            weights(TECHNICAL, 7, PROBLEM_SOLVING, 3, SYSTEM_DESIGN, 4, PROJECT, 5, BEHAVIOURAL, 5, SITUATIONAL, 3,
                    HR, 3));

    private final String label;
    private final String description;
    private final String interviewerGuidance;
    private final Map<QuestionCategory, Integer> weights;

    InterviewLevel(String label, String description, String interviewerGuidance,
            Map<QuestionCategory, Integer> weights) {
        this.label = label;
        this.description = description;
        this.interviewerGuidance = interviewerGuidance;
        this.weights = weights;
    }

    public String label() {
        return label;
    }

    /** One line for the candidate, shown when choosing a level. */
    public String description() {
        return description;
    }

    /** How the interviewer should pitch the questions; part of the session's system prompt. */
    public String interviewerGuidance() {
        return interviewerGuidance;
    }

    /** Relative share of each category. */
    public Map<QuestionCategory, Integer> weights() {
        return weights;
    }

    /** Number of questions at this level, from {@code coach.interview.*-questions}. */
    public int questions(CoachProperties.Interview settings) {
        return switch (this) {
            case EASY -> settings.easyQuestions();
            case STANDARD -> settings.standardQuestions();
            case PROFESSIONAL -> settings.professionalQuestions();
        };
    }

    private static Map<QuestionCategory, Integer> weights(Object... categoryWeightPairs) {
        Map<QuestionCategory, Integer> map = new EnumMap<>(QuestionCategory.class);
        for (int i = 0; i < categoryWeightPairs.length; i += 2) {
            map.put((QuestionCategory) categoryWeightPairs[i], (Integer) categoryWeightPairs[i + 1]);
        }
        if (map.size() != QuestionCategory.values().length) {
            throw new IllegalStateException("Every question category needs a weight");
        }
        return Collections.unmodifiableMap(map);
    }
}
