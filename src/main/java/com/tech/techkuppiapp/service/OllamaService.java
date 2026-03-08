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
 * LLM service that calls a local Ollama instance (OpenAI-compatible API).
 * Active when {@code app.ai.provider=ollama}.
 */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama")
public class OllamaService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public OllamaService(
            RestTemplate restTemplate,
            @Value("${ollama.api.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${ollama.api.model:llama3.2}") String model,
            @Value("${ollama.api.max-tokens:300}") int maxTokens,
            @Value("${ollama.api.temperature:0.7}") double temperature) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.replaceAll("/$", "") : "http://localhost:11434/v1";
        this.model = model != null && !model.isBlank() ? model : "llama3.2";
        this.maxTokens = Math.max(100, maxTokens);
        this.temperature = temperature;
    }

    @Override
    public String generate(String prompt) {
        return generate(prompt, maxTokens);
    }

    @Override
    public String generate(String prompt, int maxCompletionTokens) {
        String url = baseUrl + "/chat/completions";
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", model);
        requestJson.put("stream", false);
        requestJson.put("options", new JSONObject()
                .put("temperature", temperature)
                .put("num_predict", Math.max(100, maxCompletionTokens)));
        requestJson.put("messages", Collections.singletonList(
                new JSONObject().put("role", "user").put("content", prompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestJson.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            String content = parseContent(response.getBody());
            if (content != null) {
                log.info("Ollama model response: {}", content);
            }
            return content;
        } catch (HttpClientErrorException e) {
            log.error("Ollama API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException e) {
            log.error("Ollama request failed (network/timeout): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling Ollama", e);
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
            log.warn("Failed to parse Ollama response: {}", e.getMessage());
            return null;
        }
    }
}
