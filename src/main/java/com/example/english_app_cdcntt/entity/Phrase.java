package com.example.english_app_cdcntt.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A sentence a user submitted for grammar checking, with the corrected version and the list of
 * individual errors. The database enforces {@code score BETWEEN 0 AND 10} with
 * {@code ck_phrases_score}.
 */
@Entity
@Table(name = "phrases")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "score"})
public class Phrase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** TEXT column, not a CLOB: the migration declares {@code TEXT}, so validation must match. */
    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "corrected_text", nullable = false, columnDefinition = "TEXT")
    private String correctedText;

    @Column(name = "score", nullable = false)
    private int score;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "phrase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GrammarError> grammarErrors = new ArrayList<>();

    protected Phrase(User user, String text, String correctedText, int score) {
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException("score must be between 0 and 10");
        }
        this.user = Objects.requireNonNull(user, "user");
        this.text = Objects.requireNonNull(text, "text");
        this.correctedText = Objects.requireNonNull(correctedText, "correctedText");
        this.score = score;
    }

    public static Phrase create(User user, String text, String correctedText, int score) {
        return new Phrase(user, text, correctedText, score);
    }

    public void addError(GrammarError error) {
        Objects.requireNonNull(error, "error");
        error.assignTo(this);
        grammarErrors.add(error);
    }

    public void removeError(GrammarError error) {
        Objects.requireNonNull(error, "error");
        if (grammarErrors.remove(error)) {
            error.assignTo(null);
        }
    }

    /** Read-only view; mutate through {@link #addError(GrammarError)} / {@link #removeError(GrammarError)}. */
    public List<GrammarError> getGrammarErrors() {
        return Collections.unmodifiableList(grammarErrors);
    }

    public void updateResult(String correctedText, int score) {
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException("score must be between 0 and 10");
        }
        this.correctedText = Objects.requireNonNull(correctedText, "correctedText");
        this.score = score;
    }
}
