package com.tech.techkuppiapp.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted question from the AI-generated question bank (question text, options, correct answer).
 */
@Entity
@Table(name = "question_bank")
public class QuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String question;

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
    }

    public QuestionBank() {
    }

    public QuestionBank(String question, List<String> options, String correctAnswer) {
        this.question = question;
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
}
