package com.example.coach.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Profile details are required at sign-up; accounts created before they were collected have none. */
    @Column(name = "first_name", length = 60)
    private String firstName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    /** ISO 3166-1 alpha-2 region of the mobile number, e.g. {@code IN}. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Mobile number in E.164 form, e.g. {@code +919876543210}. */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /** When the user clicked the confirmation link; {@code null} until then (sign-in is blocked). */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {}

    public User(String email, String passwordHash, String firstName, String lastName, String countryCode,
            String phoneNumber) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.countryCode = countryCode;
        this.phoneNumber = phoneNumber;
        this.createdAt = Instant.now();
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified() {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Instant.now();
        }
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
