package com.example.coach.repository;

import com.example.coach.model.Analysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByIdAndUserId(Long id, Long userId);

    List<Analysis> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
