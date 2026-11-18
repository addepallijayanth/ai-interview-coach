package com.example.coach.model;

import com.example.coach.ai.agent.QuestionCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "interview_turn")
public class InterviewTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_no", nullable = false)
    private int questionNo;

    /** Planned category, e.g. TECHNICAL; null for interviews created before categories existed. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private QuestionCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(columnDefinition = "text")
    private String answer;

    private Integer score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_json", columnDefinition = "jsonb")
    private String feedbackJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    protected InterviewTurn() {}

    public InterviewTurn(Long sessionId, int questionNo, QuestionCategory category, String question) {
        this.sessionId = sessionId;
        this.questionNo = questionNo;
        this.category = category;
        this.question = question;
        this.createdAt = Instant.now();
    }

    public void recordAnswer(String answer) {
        this.answer = answer;
        this.answeredAt = Instant.now();
    }

    /** Re-opens the question, e.g. when the AI declined to process the answer and the candidate should rephrase. */
    public void clearAnswer() {
        this.answer = null;
        this.answeredAt = null;
    }

    public void recordScore(int score, String feedbackJson) {
        this.score = score;
        this.feedbackJson = feedbackJson;
    }

    public boolean isAnswered() {
        return answer != null;
    }

    public boolean isScored() {
        return score != null;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public int getQuestionNo() {
        return questionNo;
    }

    public QuestionCategory getCategory() {
        return category;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Integer getScore() {
        return score;
    }

    public String getFeedbackJson() {
        return feedbackJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
