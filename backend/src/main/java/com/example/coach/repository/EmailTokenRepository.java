package com.example.coach.repository;

import com.example.coach.model.EmailToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    Optional<EmailToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, EmailToken.Purpose purpose);

    /** Invalidates links that were sent but never used, e.g. when a newer link replaces them. */
    @Modifying
    @Query("delete from EmailToken t where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int deleteUnused(@Param("userId") Long userId, @Param("purpose") EmailToken.Purpose purpose);
}
