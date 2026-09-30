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
 * One meaning of a {@link Word}. A word may have several meanings, so unlike
 * {@link WordCacheValue} the example and pronunciation fields are optional.
 */
@Entity
@Table(name = "word_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "vietnamese", "partOfSpeech"})
public class WordValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "vietnamese", nullable = false, length = 1000)
    private String vietnamese;

    @Column(name = "example", length = 2000)
    private String example;

    @Column(name = "example_translation", length = 2000)
    private String exampleTranslation;

    @Column(name = "pronunciation", length = 255)
    private String pronunciation;

    @Enumerated(EnumType.STRING)
    @Column(name = "part_of_speech", length = 20)
    private PartOfSpeech partOfSpeech;

    protected WordValue(
            String vietnamese,
            String example,
            String exampleTranslation,
            String pronunciation,
            PartOfSpeech partOfSpeech) {
        this.vietnamese = Objects.requireNonNull(vietnamese, "vietnamese");
        this.example = example;
        this.exampleTranslation = exampleTranslation;
        this.pronunciation = pronunciation;
        this.partOfSpeech = partOfSpeech;
    }

    /** Static factory; only {@code vietnamese} is mandatory, the remaining fields may be null. */
    public static WordValue create(
            String vietnamese,
            String example,
            String exampleTranslation,
            String pronunciation,
            PartOfSpeech partOfSpeech) {
        return new WordValue(vietnamese, example, exampleTranslation, pronunciation, partOfSpeech);
    }

    /** Keeps both sides of the Word &lt;-&gt; WordValue association in sync. */
    void assignTo(Word word) {
        this.word = word;
    }
}
