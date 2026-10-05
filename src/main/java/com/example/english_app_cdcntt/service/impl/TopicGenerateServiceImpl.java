package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.exception.InvalidTopicException;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.TopicGenerateService;
import com.example.english_app_cdcntt.service.TopicTxService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * §7.1 — the non-transactional part of POST /api/words/generate-topic: normalize the topic,
 * have the AI confirm it (else 400 before any DB access), feed the notebook's current words
 * to the AI as the exclusion list, then hand the accepted batch to the one write transaction.
 */
@Service
@RequiredArgsConstructor
public class TopicGenerateServiceImpl implements TopicGenerateService {

    private final AiClient aiClient;
    private final WordRepository wordRepository;
    private final TopicTxService topicTxService;

    @Override
    public List<WordDto> generateTopicWords(Long userId, String rawTopic) {
        String topic = normalizeTopic(rawTopic);
        if (topic.isBlank()) {
            throw new InvalidTopicException();
        }
        if (!aiClient.checkTopic(topic)) {
            throw new InvalidTopicException();
        }
        List<String> exclude = wordRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(Word::getEnglish)
                .toList();
        List<AiClient.TopicWord> proposed = aiClient.generateTopicWords(topic, exclude);
        return topicTxService.saveBatch(userId, proposed);
    }

    /** Collapse whitespace runs, keep the caller's casing (a topic may be Vietnamese). */
    private static String normalizeTopic(String rawTopic) {
        return rawTopic == null ? "" : rawTopic.strip().replaceAll("\\s+", " ");
    }
}
