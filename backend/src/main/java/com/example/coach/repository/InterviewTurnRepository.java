package com.example.coach.repository;

import com.example.coach.model.InterviewTurn;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {

    List<InterviewTurn> findBySessionIdOrderByQuestionNoAsc(Long sessionId);

    List<InterviewTurn> findBySessionIdIn(Collection<Long> sessionIds);

    Optional<InterviewTurn> findBySessionIdAndQuestionNo(Long sessionId, int questionNo);

    Optional<InterviewTurn> findFirstBySessionIdOrderByQuestionNoDesc(Long sessionId);
}
