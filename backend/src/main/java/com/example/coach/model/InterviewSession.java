package com.example.coach.model;

import com.example.coach.ai.agent.InterviewLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "interview_session")
public class InterviewSession {

    public enum Status { ACTIVE, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "question_target", nullable = false)
    private int questionTarget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewLevel level;

    /** JSON array with the category of every planned question; null for interviews created before levels. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_plan", columnDefinition = "jsonb")
    private String questionPlanJson;

    @Column(name = "overall_score", precision = 4, scale = 1)
    private BigDecimal overallScore;

    @Column(columnDefinition = "text")
    private String summary;

    /** The agent's full message history (including tool calls and thinking blocks), replayed append-only. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript_json", nullable = false, columnDefinition = "jsonb")
    private String transcriptJson;

    @Column(name = "end_requested", nullable = false)
    private boolean endRequested;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "study_plan_json", columnDefinition = "jsonb")
    private String studyPlanJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected InterviewSession() {}

    public InterviewSession(Long userId, Long analysisId, InterviewLevel level, int questionTarget,
            String questionPlanJson) {
        this.userId = userId;
        this.analysisId = analysisId;
        this.level = level;
        this.questionTarget = questionTarget;
        this.questionPlanJson = questionPlanJson;
        this.status = Status.ACTIVE;
        this.transcriptJson = "[]";
        this.createdAt = Instant.now();
    }

    public void end(String summary, BigDecimal overallScore) {
        this.status = Status.ENDED;
        this.summary = summary;
        this.overallScore = overallScore;
        this.endedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public Status getStatus() {
        return status;
    }

    public int getQuestionTarget() {
        return questionTarget;
    }

    public InterviewLevel getLevel() {
        return level;
    }

    public String getQuestionPlanJson() {
        return questionPlanJson;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public String getSummary() {
        return summary;
    }

    public String getTranscriptJson() {
        return transcriptJson;
    }

    public void setTranscriptJson(String transcriptJson) {
        this.transcriptJson = transcriptJson;
    }

    public boolean isEndRequested() {
        return endRequested;
    }

    public void setEndRequested(boolean endRequested) {
        this.endRequested = endRequested;
    }

    public String getStudyPlanJson() {
        return studyPlanJson;
    }

    public void setStudyPlanJson(String studyPlanJson) {
        this.studyPlanJson = studyPlanJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
