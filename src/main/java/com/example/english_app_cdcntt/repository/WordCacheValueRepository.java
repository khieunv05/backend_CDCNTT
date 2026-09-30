package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.WordCacheValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordCacheValueRepository extends JpaRepository<WordCacheValue, Long> {

    List<WordCacheValue> findByWordCache_Id(Long wordCacheId);
}
