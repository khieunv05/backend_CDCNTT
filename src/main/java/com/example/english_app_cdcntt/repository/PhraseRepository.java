package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.Phrase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhraseRepository extends JpaRepository<Phrase, Long> {

    /** Ownership is part of the predicate so an id alone can never leak another user's phrase. */
    Optional<Phrase> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = {"grammarErrors"})
    @Query("select p from Phrase p where p.id = :phraseId and p.user.id = :userId")
    Optional<Phrase> findOwnedWithGrammarErrors(@Param("phraseId") Long phraseId, @Param("userId") Long userId);

    List<Phrase> findByUser_Id(Long userId);

    /**
     * §8.1 — GET /api/phrases: the user's history, newest first (createdAt DESC), errors fetched
     * in-tx. {@code id} is the tiebreaker so phrases created inside the same microsecond — a burst
     * of creates, or the coarse clock of a test — still come back newest first.
     */
    @EntityGraph(attributePaths = {"grammarErrors"})
    List<Phrase> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    /** Every phrase of the user, ascending id — bulk cleanup in tests. */
    @EntityGraph(attributePaths = {"grammarErrors"})
    List<Phrase> findByUser_IdOrderByIdAsc(Long userId);

    /** Row lock for the owned delete (mirrors WordRepository.findOwnedForUpdate). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Phrase p where p.id = :phraseId and p.user.id = :userId")
    Optional<Phrase> findOwnedForUpdate(@Param("phraseId") Long phraseId, @Param("userId") Long userId);

    long countByUser_Id(Long userId);
}
