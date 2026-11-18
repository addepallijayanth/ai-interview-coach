package com.example.coach.ai.agent;

import static com.example.coach.ai.agent.QuestionCategory.HR;
import static com.example.coach.ai.agent.QuestionCategory.PROJECT;
import static com.example.coach.ai.agent.QuestionCategory.SYSTEM_DESIGN;
import static com.example.coach.ai.agent.QuestionCategory.TECHNICAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterviewPlanTest {

    @ParameterizedTest
    @CsvSource({"EASY, 10", "STANDARD, 20", "PROFESSIONAL, 30"})
    void everyLevelMixesAllKindsOfQuestionAndOpensAndClosesLikeARealInterview(InterviewLevel level, int questions) {
        List<QuestionCategory> plan = InterviewPlan.build(level, questions);

        assertThat(plan).hasSize(questions);
        assertThat(counts(plan)).isEqualTo(level.weights().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(() -> new EnumMap<QuestionCategory, Integer>(QuestionCategory.class),
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll));
        assertThat(plan.getFirst()).isEqualTo(HR);     // tell me about yourself
        assertThat(plan.get(1)).isEqualTo(PROJECT);    // walk me through a project on your resume
        assertThat(plan.getLast()).isEqualTo(HR);      // why this role
        assertThat(plan.subList(1, plan.size() - 1)).contains(TECHNICAL, QuestionCategory.BEHAVIOURAL,
                QuestionCategory.SITUATIONAL, QuestionCategory.PROBLEM_SOLVING);
    }

    @Test
    void theStandardAndProfessionalLevelsIncludeSystemDesignButEasyDoesNot() {
        assertThat(InterviewPlan.build(InterviewLevel.EASY, 10)).doesNotContain(SYSTEM_DESIGN);
        assertThat(counts(InterviewPlan.build(InterviewLevel.STANDARD, 20)).get(SYSTEM_DESIGN)).isEqualTo(2);
        assertThat(counts(InterviewPlan.build(InterviewLevel.PROFESSIONAL, 30)).get(SYSTEM_DESIGN)).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"EASY, 10", "STANDARD, 20", "PROFESSIONAL, 30"})
    void categoriesAreSpreadOutRatherThanBunched(InterviewLevel level, int questions) {
        List<QuestionCategory> plan = InterviewPlan.build(level, questions);
        int repeats = 0;
        for (int i = 1; i < plan.size(); i++) {
            if (plan.get(i) == plan.get(i - 1)) {
                repeats++;
            }
        }
        assertThat(repeats).as("back-to-back repeats in %s", plan).isLessThanOrEqualTo(1);
    }

    @Test
    void otherSizesKeepTheMixProportionalAndStartWithAnIntroduction() {
        assertThat(InterviewPlan.build(InterviewLevel.EASY, 2)).containsExactly(HR, TECHNICAL);
        assertThat(InterviewPlan.build(InterviewLevel.EASY, 1)).containsExactly(TECHNICAL);
        List<QuestionCategory> fifteen = InterviewPlan.build(InterviewLevel.STANDARD, 15);
        assertThat(fifteen).hasSize(15).startsWith(HR, PROJECT).endsWith(HR);
        assertThat(InterviewPlan.breakdown(InterviewLevel.STANDARD, 15).values().stream().mapToInt(Integer::intValue)
                .sum()).isEqualTo(15);
    }

    @Test
    void plansAreDeterministic() {
        assertThat(InterviewPlan.build(InterviewLevel.PROFESSIONAL, 30))
                .isEqualTo(InterviewPlan.build(InterviewLevel.PROFESSIONAL, 30));
    }

    private static Map<QuestionCategory, Integer> counts(List<QuestionCategory> plan) {
        Map<QuestionCategory, Integer> counts = new EnumMap<>(QuestionCategory.class);
        plan.forEach(category -> counts.merge(category, 1, Integer::sum));
        return counts;
    }
}
