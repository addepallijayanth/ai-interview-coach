package com.example.coach.repository;

import com.example.coach.model.InterviewSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    List<InterviewSession> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
