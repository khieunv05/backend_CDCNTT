package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.Word;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Ownership is always part of the query, never a post-filter: {@code userId} appears in every
 * method so a caller can never reach another user's words.
 */
public interface WordRepository extends JpaRepository<Word, Long> {

    /**
     * Full word with its meanings for the API response. {@code LEFT JOIN FETCH} keeps words that
     * have no values yet; the {@code distinct} is required because the join multiplies rows.
     */
    @Query("""
            select distinct w from Word w
            left join fetch w.wordValues
            where w.id = :wordId and w.user.id = :userId
            """)
    Optional<Word> findOwnedWithValues(@Param("wordId") Long wordId, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<Word> findByIdAndUser_Id(Long wordId, Long userId);

    @EntityGraph(attributePaths = {"wordValues"})
    Optional<Word> findByUser_IdAndEnglish(Long userId, String english);

    @EntityGraph(attributePaths = {"wordValues"})
    Page<Word> findByUser_Id(Long userId, Pageable pageable);

    /** GET /api/words: the current user's notebook, meanings fetched, ordered by id (§6.2). */
    @EntityGraph(attributePaths = {"wordValues"})
    List<Word> findByUser_IdOrderByIdAsc(Long userId);

    @EntityGraph(attributePaths = {"wordValues"})
    List<Word> findByUser_IdAndNextReviewLessThanEqual(Long userId, Instant cutoff);

    long countByUser_IdAndNextReviewLessThanEqual(Long userId, Instant cutoff);

    /** Used by Phase 1.4-style verification; the row lock is only meaningful inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Word w where w.id = :wordId and w.user.id = :userId")
    Optional<Word> findOwnedForUpdate(@Param("wordId") Long wordId, @Param("userId") Long userId);

    /**
     * §6.3:292 — batch row lock for the review transaction. Rows are returned (and locked by
     * InnoDB) in ascending id order so concurrent batches touching the same words always acquire
     * the locks in the same sequence — no deadlock, no lost update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Word w where w.user.id = :userId and w.id in :ids order by w.id asc")
    List<Word> findOwnedForUpdate(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    boolean existsByUser_IdAndEnglish(Long userId, String english);
}
