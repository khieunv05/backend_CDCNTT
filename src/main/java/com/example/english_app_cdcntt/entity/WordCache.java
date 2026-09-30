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
 * Global cache of AI-generated word data, shared by all users and keyed by the unique
 * {@code english} column. Cache rows are never tied to a user; a user's own {@link Word} rows are
 * separate. Every column except the audit fields is mandatory here.
 */
@Entity
@Table(name = "word_cache")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "english", "level"})
public class WordCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @Column(name = "english", nullable = false, length = 255)
    private String english;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 2)
    private Level level;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "wordCache", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WordCacheValue> wordCacheValues = new ArrayList<>();

    protected WordCache(String english, Level level) {
        this.english = Objects.requireNonNull(english, "english");
        this.level = Objects.requireNonNull(level, "level");
    }

    public static WordCache create(String english, Level level) {
        return new WordCache(english, level);
    }

    public void addValue(WordCacheValue value) {
        Objects.requireNonNull(value, "value");
        value.assignTo(this);
        wordCacheValues.add(value);
    }

    public void removeValue(WordCacheValue value) {
        Objects.requireNonNull(value, "value");
        if (wordCacheValues.remove(value)) {
            value.assignTo(null);
        }
    }

    /** Read-only view; mutate through {@link #addValue(WordCacheValue)} / {@link #removeValue(WordCacheValue)}. */
    public List<WordCacheValue> getWordCacheValues() {
        return Collections.unmodifiableList(wordCacheValues);
    }

    public void updateLevel(Level level) {
        this.level = Objects.requireNonNull(level, "level");
    }
}
