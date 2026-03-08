package com.tech.techkuppiapp.service;

/**
 * Abstraction for LLM-based text generation (OpenAI or Ollama).
 * Allows switching between providers via {@code app.ai.provider}.
 */
public interface LLMService {

    /**
     * Generate a completion for the given prompt using default max tokens.
     */
    String generate(String prompt);

    /**
     * Generate a completion for the given prompt with a custom max token limit.
     */
    String generate(String prompt, int maxTokens);
}
