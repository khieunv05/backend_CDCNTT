package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.enums.Level;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Shared cache: no user scoping, uniqueness is on {@code english} alone. Entries are permanent —
 * the cache is an AI-generated knowledge base shared by all users and is never evicted by age.
 */
public interface WordCacheRepository extends JpaRepository<WordCache, Long> {

    @EntityGraph(attributePaths = {"wordCacheValues"})
    Optional<WordCache> findByEnglish(String english);

    @EntityGraph(attributePaths = {"wordCacheValues"})
    Optional<WordCache> findWithValuesById(Long id);

    @EntityGraph(attributePaths = {"wordCacheValues"})
    List<WordCache> findByLevel(Level level);

    boolean existsByEnglish(String english);
}
