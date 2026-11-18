package com.example.coach.repository;

import com.example.coach.model.Resume;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    List<Resume> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
