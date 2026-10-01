package com.example.english_app_cdcntt.mapper;

import com.example.english_app_cdcntt.dto.GrammarErrorDto;
import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.entity.GrammarError;
import com.example.english_app_cdcntt.entity.Phrase;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** §8.1 — entity → API mapping; call inside a transaction: the error collection is LAZY. */
@Component
public class PhraseMapper {

    public PhraseDto toDto(Phrase phrase) {
        List<GrammarErrorDto> errors = phrase.getGrammarErrors().stream()
                .sorted(Comparator.comparing(GrammarError::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toErrorDto)
                .toList();
        return new PhraseDto(phrase.getId(), phrase.getText(), phrase.getCorrectedText(),
                phrase.getScore(), phrase.getCreatedAt(), List.copyOf(errors));
    }

    private GrammarErrorDto toErrorDto(GrammarError error) {
        return new GrammarErrorDto(error.getIncorrect(), error.getCorrection(),
                error.getExplanation());
    }
}
