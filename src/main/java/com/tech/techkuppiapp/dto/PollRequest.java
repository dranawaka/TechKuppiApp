package com.tech.techkuppiapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class PollRequest {

    @NotBlank(message = "Question must not be blank")
    @Size(max = 300)
    private String question;

    @NotEmpty(message = "At least 2 options required")
    @Size(min = 2, max = 10)
    private List<@NotBlank(message = "Option must not be blank") String> options;

    /** Correct answer letter: A, B, C, or D. Defaults to A if not set. */
    @Size(max = 1)
    private String correctAnswer = "A";

    public PollRequest() {
    }

    public PollRequest(String question, List<String> options) {
        this.question = question;
        this.options = options;
    }

    public PollRequest(String question, List<String> options, String correctAnswer) {
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer != null ? correctAnswer : "A";
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
        this.correctAnswer = correctAnswer != null ? correctAnswer : "A";
    }
}