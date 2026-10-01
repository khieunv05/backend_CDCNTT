package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordCacheValue;
import com.example.english_app_cdcntt.mapper.WordCacheMapper;
import com.example.english_app_cdcntt.repository.WordCacheRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.WordCacheTxService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordCacheTxServiceImpl implements WordCacheTxService {

    private final WordCacheRepository wordCacheRepository;
    private final WordCacheMapper wordCacheMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<WordCache> findCached(String english) {
        return wordCacheRepository.findByEnglish(english);
    }

    @Override
    @Transactional
    public GeneratedWordDto saveNew(String english, AiClient.GeneratedMeaning meaning) {
        WordCache cache = WordCache.create(english, meaning.level());
        for (AiClient.MeaningItem item : meaning.values()) {
            cache.addValue(WordCacheValue.create(
                    item.vietnamese(),
                    item.example(),
                    item.exampleTranslation(),
                    item.pronunciation(),
                    item.partOfSpeech()));
        }
        wordCacheRepository.saveAndFlush(cache);
        return wordCacheMapper.toGenerated(cache);
    }
}
