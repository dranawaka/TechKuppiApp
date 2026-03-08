package com.tech.techkuppiapp.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * LLM service that calls the Google Gemini API (e.g. Gemini 3 Flash).
 * Active when {@code app.ai.provider=gemini}.
 */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public GeminiService(
            RestTemplate restTemplate,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-2.0-flash}") String model,  // or gemini-3-flash for Gemini 3 Flash
            @Value("${gemini.api.max-tokens:300}") int maxTokens,
            @Value("${gemini.api.temperature:0.7}") double temperature) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null && !model.isBlank() ? model : "gemini-2.0-flash";
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    @Override
    public String generate(String prompt) {
        return generate(prompt, maxTokens);
    }

    @Override
    public String generate(String prompt, int maxCompletionTokens) {
        if (apiKey.isBlank()) {
            log.warn("Gemini API key not configured (gemini.api.key)");
            return null;
        }

        String url = GEMINI_BASE + "/" + model + ":generateContent";

        JSONObject requestJson = new JSONObject();
        requestJson.put("contents", Collections.singletonList(
                new JSONObject()
                        .put("role", "user")
                        .put("parts", Collections.singletonList(new JSONObject().put("text", prompt)))));
        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", temperature);
        genConfig.put("maxOutputTokens", Math.max(100, maxCompletionTokens));
        requestJson.put("generationConfig", genConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(requestJson.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            String content = parseContent(response.getBody());
            if (content != null) {
                log.info("Gemini model response: {}", content);
            }
            return content;
        } catch (HttpClientErrorException e) {
            log.error("Gemini API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException e) {
            log.error("Gemini request failed (network/timeout): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling Gemini", e);
            return null;
        }
    }

    private String parseContent(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JSONObject json = new JSONObject(body);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.isEmpty()) return null;
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return null;
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.isEmpty()) return null;
            return parts.getJSONObject(0).optString("text", null);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return null;
        }
    }
}
