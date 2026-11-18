package com.example.coach.service;

import com.example.coach.ai.agent.InterviewPlan;
import com.example.coach.ai.agent.QuestionCategory;
import com.example.coach.dto.InterviewDtos.AnswerFeedback;
import com.example.coach.dto.InterviewDtos.TurnResponse;
import com.example.coach.dto.ReportDtos.CategoryScore;
import com.example.coach.model.InterviewSession;
import com.example.coach.model.InterviewTurn;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Shared mapping and scoring helpers for interview turns. */
@Component
public class InterviewMapper {

    private final ObjectMapper objectMapper;

    public InterviewMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TurnResponse toTurn(InterviewTurn turn) {
        QuestionCategory category = turn.getCategory();
        return new TurnResponse(turn.getQuestionNo(), category == null ? null : category.name(),
                category == null ? null : category.label(), turn.getQuestion(), turn.getAnswer(), turn.getScore(),
                feedback(turn));
    }

    public AnswerFeedback feedback(InterviewTurn turn) {
        if (turn.getFeedbackJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(turn.getFeedbackJson(), AnswerFeedback.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored feedback could not be read", e);
        }
    }

    /** The session's question plan. Interviews created before plans were stored get one built for their size. */
    public List<QuestionCategory> plan(InterviewSession session) {
        if (session.getQuestionPlanJson() == null) {
            return InterviewPlan.build(session.getLevel(), session.getQuestionTarget());
        }
        try {
            return objectMapper.readValue(session.getQuestionPlanJson(), new TypeReference<List<QuestionCategory>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored question plan could not be read", e);
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int scoredCount(List<InterviewTurn> turns) {
        return (int) turns.stream().filter(InterviewTurn::isScored).count();
    }

    /** Average answer score out of 10, one decimal place; {@code null} when nothing was scored. */
    public static BigDecimal overallScore(List<InterviewTurn> turns) {
        return average(turns.stream().map(InterviewTurn::getScore).filter(Objects::nonNull).toList());
    }

    /** Questions asked and average score per category, in category order; categories never asked are left out. */
    public static List<CategoryScore> categoryScores(List<InterviewTurn> turns) {
        Map<QuestionCategory, List<InterviewTurn>> byCategory = new EnumMap<>(QuestionCategory.class);
        for (InterviewTurn turn : turns) {
            if (turn.getCategory() != null) {
                byCategory.computeIfAbsent(turn.getCategory(), category -> new ArrayList<>()).add(turn);
            }
        }
        return byCategory.entrySet().stream().map(entry -> {
            List<Integer> scores = entry.getValue().stream().map(InterviewTurn::getScore).filter(Objects::nonNull)
                    .toList();
            return new CategoryScore(entry.getKey().name(), entry.getKey().label(), entry.getValue().size(),
                    scores.size(), average(scores));
        }).toList();
    }

    private static BigDecimal average(List<Integer> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        int total = scores.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
    }
}
