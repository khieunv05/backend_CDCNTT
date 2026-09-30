package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Token is a 2048-char random string, globally unique — no user scope needed here. */
    @EntityGraph(attributePaths = {"user"})
    Optional<RefreshToken> findByToken(String token);

    /**
     * Locking read for rotation (§5.2:240): {@code SELECT ... FOR UPDATE} makes two concurrent
     * refreshes of the same token serialize — the loser waits for the winner's commit, then
     * re-reads and finds the row gone → INVALID → 401, exactly the specified behavior.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    List<RefreshToken> findByUser_Id(Long userId);

    /** Revocation on logout; bulk delete keeps it a single statement. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.token = :token")
    int deleteByTokenValue(@Param("token") String token);

    /** Scheduled cleanup of expired sessions. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiryDate < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
