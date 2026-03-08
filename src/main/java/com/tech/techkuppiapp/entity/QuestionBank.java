package com.tech.techkuppiapp.entity;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted question from the AI-generated question bank (question text, options, correct answer).
 * Uniqueness is enforced by question_hash (normalized: trim + lowercase, then SHA-256).
 */
@Entity
@Table(name = "question_bank", uniqueConstraints = @UniqueConstraint(columnNames = "question_hash"))
public class QuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String question;

    /** Hash of normalized question (trim + lowercase) for duplicate detection. Nullable to allow existing rows before backfill. */
    @Column(name = "question_hash", unique = true, length = 64)
    private String questionHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_bank_options", joinColumns = @JoinColumn(name = "question_bank_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text", length = 1000)
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_answer", nullable = false, length = 1)
    private String correctAnswer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (questionHash == null && question != null) {
            questionHash = computeQuestionHash(question);
        }
    }

    /** Normalized hash for duplicate detection: SHA-256 of trimmed lowercase question. */
    public static String computeQuestionHash(String questionText) {
        if (questionText == null) return null;
        String normalized = questionText.trim().toLowerCase();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public QuestionBank() {
    }

    public QuestionBank(String question, List<String> options, String correctAnswer) {
        this.question = question;
        this.questionHash = question != null ? computeQuestionHash(question) : null;
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
        this.correctAnswer = correctAnswer != null ? correctAnswer.trim().toUpperCase() : "A";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer != null ? correctAnswer.trim().toUpperCase() : "A";
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public void setQuestionHash(String questionHash) {
        this.questionHash = questionHash;
    }
}
