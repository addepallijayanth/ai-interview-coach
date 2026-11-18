package com.example.coach.repository;

import com.example.coach.model.JobDescription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    Optional<JobDescription> findByIdAndUserId(Long id, Long userId);

    List<JobDescription> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
