package com.example.coach.ai.agent;

import static com.example.coach.ai.agent.QuestionCategory.BEHAVIOURAL;
import static com.example.coach.ai.agent.QuestionCategory.HR;
import static com.example.coach.ai.agent.QuestionCategory.PROBLEM_SOLVING;
import static com.example.coach.ai.agent.QuestionCategory.PROJECT;
import static com.example.coach.ai.agent.QuestionCategory.SITUATIONAL;
import static com.example.coach.ai.agent.QuestionCategory.SYSTEM_DESIGN;
import static com.example.coach.ai.agent.QuestionCategory.TECHNICAL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decides the category of every question before the interview starts, so the mix is guaranteed (not left to the
 * model) and the plan can sit in the cached system prompt.
 *
 * <p>The plan opens like a real interview ("tell me about yourself", then a project from the resume), closes
 * with a motivation question, and spreads the remaining categories evenly so the same kind of question rarely
 * comes twice in a row. It is deterministic: the same level and size always give the same plan.
 */
public final class InterviewPlan {

    /** Who gets a leftover question when the level's mix does not divide evenly. HR first, for the opener. */
    private static final List<QuestionCategory> APPORTION_ORDER = List.of(HR, TECHNICAL, PROJECT, BEHAVIOURAL,
            PROBLEM_SOLVING, SITUATIONAL, SYSTEM_DESIGN);
    /** Tie-break order while interleaving: conversational categories before heavy design questions. */
    private static final List<QuestionCategory> INTERLEAVE_ORDER = List.of(PROJECT, TECHNICAL, BEHAVIOURAL,
            PROBLEM_SOLVING, SITUATIONAL, SYSTEM_DESIGN, HR);

    private InterviewPlan() {}

    /** How many questions of each category an interview of {@code questions} questions at {@code level} has. */
    public static Map<QuestionCategory, Integer> breakdown(InterviewLevel level, int questions) {
        if (questions < 1) {
            throw new IllegalArgumentException("An interview needs at least one question");
        }
        Map<QuestionCategory, Integer> weights = level.weights();
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        // Largest-remainder apportionment: floor of each exact share, then hand out what is left by remainder.
        Map<QuestionCategory, Integer> counts = new EnumMap<>(QuestionCategory.class);
        Map<QuestionCategory, Integer> remainders = new EnumMap<>(QuestionCategory.class);
        int assigned = 0;
        for (QuestionCategory category : QuestionCategory.values()) {
            int share = weights.get(category) * questions;
            counts.put(category, share / totalWeight);
            remainders.put(category, share % totalWeight);
            assigned += share / totalWeight;
        }
        List<QuestionCategory> byRemainder = new ArrayList<>(APPORTION_ORDER);
        byRemainder.sort((a, b) -> Integer.compare(remainders.get(b), remainders.get(a))); // stable: keeps order on ties
        for (int i = 0; assigned < questions; i++, assigned++) {
            counts.merge(byRemainder.get(i % byRemainder.size()), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** The category of each question, in order: element 0 is question 1. */
    public static List<QuestionCategory> build(InterviewLevel level, int questions) {
        Map<QuestionCategory, Integer> remaining = new EnumMap<>(breakdown(level, questions));
        List<QuestionCategory> plan = new ArrayList<>(questions);

        boolean opener = take(remaining, HR);
        if (opener) {
            plan.add(HR); // "Tell me about yourself"
        }
        boolean closer = take(remaining, HR); // saved for the last question: motivation and fit
        if (take(remaining, PROJECT)) {
            plan.add(PROJECT); // the natural follow-up to an introduction
        }
        plan.addAll(interleave(remaining));
        if (closer) {
            plan.add(HR);
        }
        return List.copyOf(plan);
    }

    private static boolean take(Map<QuestionCategory, Integer> counts, QuestionCategory category) {
        if (counts.getOrDefault(category, 0) == 0) {
            return false;
        }
        counts.merge(category, -1, Integer::sum);
        return true;
    }

    /**
     * Smooth weighted round-robin (the algorithm nginx uses to spread requests across weighted servers): over one
     * full cycle every category appears exactly as often as its count, as evenly spaced as possible.
     */
    private static List<QuestionCategory> interleave(Map<QuestionCategory, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<QuestionCategory, Integer> current = new EnumMap<>(QuestionCategory.class);
        List<QuestionCategory> sequence = new ArrayList<>(total);
        for (int step = 0; step < total; step++) {
            QuestionCategory best = null;
            for (QuestionCategory category : INTERLEAVE_ORDER) {
                int weight = counts.getOrDefault(category, 0);
                if (weight == 0) {
                    continue;
                }
                int value = current.merge(category, weight, Integer::sum);
                if (best == null || value > current.get(best)) {
                    best = category;
                }
            }
            current.merge(best, -total, Integer::sum);
            sequence.add(best);
        }
        return sequence;
    }
}
