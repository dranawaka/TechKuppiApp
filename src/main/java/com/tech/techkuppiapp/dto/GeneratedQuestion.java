package com.tech.techkuppiapp.dto;

import java.util.List;

/**
 * Represents a generated multiple-choice question (e.g. from OpenAI).
 * correctAnswer is the letter: "A", "B", "C", or "D".
 */
public class GeneratedQuestion {
    private String question;
    private List<String> options;
    private String correctAnswer;

    public GeneratedQuestion() {
    }

    public GeneratedQuestion(String question, List<String> options) {
        this.question = question;
        this.options = options;
    }

    public GeneratedQuestion(String question, List<String> options, String correctAnswer) {
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer != null ? correctAnswer.toUpperCase() : null;
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
        this.options = options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer != null ? correctAnswer.toUpperCase() : null;
    }
}
