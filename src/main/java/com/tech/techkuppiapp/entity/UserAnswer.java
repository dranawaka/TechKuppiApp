package com.tech.techkuppiapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A user's answer to a quiz question (stored for leaderboard).
 */
@Entity
@Table(name = "user_answer", indexes = {
        @Index(name = "idx_user_answer_user_id", columnList = "user_id"),
        @Index(name = "idx_user_answer_correct", columnList = "correct")
})
public class UserAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private QuizUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_bank_id")
    private QuestionBank questionBank;

    @Column(name = "chosen_answer", nullable = false, length = 1)
    private String chosenAnswer;

    @Column(name = "correct", nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @PrePersist
    protected void onCreate() {
        if (answeredAt == null) answeredAt = Instant.now();
    }

    public UserAnswer() {
    }

    public UserAnswer(QuizUser user, QuestionBank questionBank, String chosenAnswer, boolean correct, Instant answeredAt) {
        this.user = user;
        this.questionBank = questionBank;
        this.chosenAnswer = chosenAnswer != null ? chosenAnswer.toUpperCase() : "A";
        this.correct = correct;
        this.answeredAt = answeredAt != null ? answeredAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuizUser getUser() {
        return user;
    }

    public void setUser(QuizUser user) {
        this.user = user;
    }

    public QuestionBank getQuestionBank() {
        return questionBank;
    }

    public void setQuestionBank(QuestionBank questionBank) {
        this.questionBank = questionBank;
    }

    public String getChosenAnswer() {
        return chosenAnswer;
    }

    public void setChosenAnswer(String chosenAnswer) {
        this.chosenAnswer = chosenAnswer != null ? chosenAnswer.toUpperCase() : "A";
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }
}
