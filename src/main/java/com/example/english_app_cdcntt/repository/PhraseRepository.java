package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.Phrase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Phrase> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Phrase> findByUser_Id(Long userId);

    /** §8.1 — GET /api/phrases: the user's history in id ASC order, errors fetched in-tx. */
    @EntityGraph(attributePaths = {"grammarErrors"})
    List<Phrase> findByUser_IdOrderByIdAsc(Long userId);

    /** Row lock for the owned delete (mirrors WordRepository.findOwnedForUpdate). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Phrase p where p.id = :phraseId and p.user.id = :userId")
    Optional<Phrase> findOwnedForUpdate(@Param("phraseId") Long phraseId, @Param("userId") Long userId);

    long countByUser_Id(Long userId);
}
