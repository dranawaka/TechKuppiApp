package com.tech.techkuppiapp.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Service
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public OpenAIService(
            RestTemplate restTemplate,
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.model:gpt-5.4}") String model,
            @Value("${openai.api.max-tokens:300}") int maxTokens,
            @Value("${openai.api.temperature:0.7}") double temperature) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    /**
     * Calls OpenAI Chat API and returns the assistant message content.
     *
     * @param prompt the user prompt
     * @return content of the first choice, or null on failure
     */
    public String getChatGPTResponse(String prompt) {
        return getChatGPTResponse(prompt, maxTokens);
    }

    /**
     * Calls OpenAI Chat API with a custom max token limit (e.g. for batch responses).
     */
    public String getChatGPTResponse(String prompt, int maxCompletionTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API key not configured");
            return null;
        }

        JSONObject requestJson = new JSONObject();
        requestJson.put("model", model);
        requestJson.put("max_completion_tokens", Math.max(100, maxCompletionTokens));
        requestJson.put("temperature", temperature);
        requestJson.put("messages", Collections.singletonList(
                new JSONObject().put("role", "user").put("content", prompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(requestJson.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_URL, HttpMethod.POST, entity, String.class);
            return parseContent(response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("OpenAI API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException e) {
            log.error("OpenAI request failed (network/timeout): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling OpenAI", e);
            return null;
        }
    }

    private String parseContent(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JSONObject json = new JSONObject(body);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            return choices.getJSONObject(0)
                    .optJSONObject("message")
                    .optString("content", null);
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI response: {}", e.getMessage());
            return null;
        }
    }
}
