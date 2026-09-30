package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.WordValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WordValueRepository extends JpaRepository<WordValue, Long> {

    List<WordValue> findByWord_Id(Long wordId);

    /** Bulk delete used when a word is rewritten; bypasses the persistence context on purpose. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WordValue v where v.word.id = :wordId")
    int deleteByWord_Id(@Param("wordId") Long wordId);
}
