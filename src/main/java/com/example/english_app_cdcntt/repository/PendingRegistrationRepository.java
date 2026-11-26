package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.PendingRegistration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /** Email lookup is case-insensitive because of the migration collation (ai_ci). */
    Optional<PendingRegistration> findByEmail(String email);

    Optional<PendingRegistration> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Scheduled cleanup; inclusive like the refresh-token cleanup. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PendingRegistration p where p.expiresAt <= :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
