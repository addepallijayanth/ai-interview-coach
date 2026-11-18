package com.example.coach.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_description")
public class JobDescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 200)
    private String title;

    @Column(length = 200)
    private String company;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements_json", columnDefinition = "jsonb")
    private String requirementsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobDescription() {}

    public JobDescription(Long userId, String title, String company, String text) {
        this.userId = userId;
        this.title = title;
        this.company = company;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getText() {
        return text;
    }

    public String getRequirementsJson() {
        return requirementsJson;
    }

    public void setRequirementsJson(String requirementsJson) {
        this.requirementsJson = requirementsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
