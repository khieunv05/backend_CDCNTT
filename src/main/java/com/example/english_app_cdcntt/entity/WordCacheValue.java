package com.example.english_app_cdcntt.entity;

import com.example.english_app_cdcntt.enums.PartOfSpeech;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One cached meaning of a {@link WordCache} entry. Every field is mandatory: cached data is written
 * only once the AI response is complete.
 */
@Entity
@Table(name = "word_cache_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "vietnamese", "partOfSpeech"})
public class WordCacheValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_cache_id", nullable = false)
    private WordCache wordCache;

    @Column(name = "vietnamese", nullable = false, length = 1000)
    private String vietnamese;

    @Column(name = "example", nullable = false, length = 2000)
    private String example;

    @Column(name = "example_translation", nullable = false, length = 2000)
    private String exampleTranslation;

    @Column(name = "pronunciation", nullable = false, length = 255)
    private String pronunciation;

    @Enumerated(EnumType.STRING)
    @Column(name = "part_of_speech", nullable = false, length = 20)
    private PartOfSpeech partOfSpeech;

    protected WordCacheValue(
            String vietnamese,
            String example,
            String exampleTranslation,
            String pronunciation,
            PartOfSpeech partOfSpeech) {
        this.vietnamese = Objects.requireNonNull(vietnamese, "vietnamese");
        this.example = Objects.requireNonNull(example, "example");
        this.exampleTranslation = Objects.requireNonNull(exampleTranslation, "exampleTranslation");
        this.pronunciation = Objects.requireNonNull(pronunciation, "pronunciation");
        this.partOfSpeech = Objects.requireNonNull(partOfSpeech, "partOfSpeech");
    }

    /** Static factory; every field is mandatory, mirroring the NOT NULL columns. */
    public static WordCacheValue create(
            String vietnamese,
            String example,
            String exampleTranslation,
            String pronunciation,
            PartOfSpeech partOfSpeech) {
        return new WordCacheValue(vietnamese, example, exampleTranslation, pronunciation, partOfSpeech);
    }

    /** Keeps both sides of the WordCache &lt;-&gt; WordCacheValue association in sync. */
    void assignTo(WordCache wordCache) {
        this.wordCache = wordCache;
    }
}
