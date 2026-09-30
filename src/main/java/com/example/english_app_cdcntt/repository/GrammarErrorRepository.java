package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.GrammarError;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrammarErrorRepository extends JpaRepository<GrammarError, Long> {

    List<GrammarError> findByPhrase_Id(Long phraseId);

    long countByPhrase_Id(Long phraseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GrammarError e where e.phrase.id = :phraseId")
    int deleteByPhrase_Id(@Param("phraseId") Long phraseId);
}
