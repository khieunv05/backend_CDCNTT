package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.PasswordResetCode;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** {@code UNIQUE (user_id)} makes this at most one row: the code currently in force. */
    Optional<PasswordResetCode> findByUser_Id(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetCode c where c.expiresAt <= :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
