package com.example.english_app_cdcntt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One mistake found in a {@link Phrase}, with its correction and explanation. */
@Entity
@Table(name = "grammar_errors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id"})
public class GrammarError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phrase_id", nullable = false)
    private Phrase phrase;

    @Column(name = "incorrect", nullable = false, columnDefinition = "TEXT")
    private String incorrect;

    @Column(name = "correction", nullable = false, columnDefinition = "TEXT")
    private String correction;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    protected GrammarError(String incorrect, String correction, String explanation) {
        this.incorrect = Objects.requireNonNull(incorrect, "incorrect");
        this.correction = Objects.requireNonNull(correction, "correction");
        this.explanation = Objects.requireNonNull(explanation, "explanation");
    }

    /** Static factory; every field is mandatory, mirroring the NOT NULL columns. */
    public static GrammarError create(String incorrect, String correction, String explanation) {
        return new GrammarError(incorrect, correction, explanation);
    }

    /** Keeps both sides of the Phrase &lt;-&gt; GrammarError association in sync. */
    void assignTo(Phrase phrase) {
        this.phrase = phrase;
    }
}
