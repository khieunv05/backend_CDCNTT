package com.example.english_app_cdcntt.entity;

import com.example.english_app_cdcntt.enums.Level;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A word a user is learning, together with its spaced-repetition state. The pair
 * {@code (user_id, english)} is unique; the database also enforces {@code review_count >= 0} with
 * {@code ck_words_review_count}, so this entity does not repeat that check.
 */
@Entity
@Table(name = "words")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "english", "level", "reviewCount"})
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "english", nullable = false, length = 255)
    private String english;

    /** {@code null} while the level has not been determined yet. */
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 2)
    private Level level;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "next_review", nullable = false)
    private Instant nextReview;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WordValue> wordValues = new ArrayList<>();

    protected Word(User user, String english, Level level, Instant nextReview) {
        this.user = Objects.requireNonNull(user, "user");
        this.english = Objects.requireNonNull(english, "english");
        this.level = level;
        this.nextReview = Objects.requireNonNull(nextReview, "nextReview");
    }

    public static Word create(User user, String english, Instant nextReview) {
        return new Word(user, english, null, nextReview);
    }

    public void addValue(WordValue value) {
        Objects.requireNonNull(value, "value");
        value.assignTo(this);
        wordValues.add(value);
    }

    public void removeValue(WordValue value) {
        Objects.requireNonNull(value, "value");
        if (wordValues.remove(value)) {
            value.assignTo(null);
        }
    }

    /** Read-only view; mutate through {@link #addValue(WordValue)} / {@link #removeValue(WordValue)}. */
    public List<WordValue> getWordValues() {
        return Collections.unmodifiableList(wordValues);
    }

    public void rename(String english) {
        this.english = Objects.requireNonNull(english, "english");
    }

    public void updateLevel(Level level) {
        this.level = level;
    }

    /** Applies the result of one review. Callers must keep {@code reviewCount} non-negative. */
    public void scheduleReview(int reviewCount, Instant nextReview) {
        if (reviewCount < 0) {
            throw new IllegalArgumentException("reviewCount must not be negative");
        }
        this.reviewCount = reviewCount;
        this.nextReview = Objects.requireNonNull(nextReview, "nextReview");
    }
}
