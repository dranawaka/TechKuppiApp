package com.tech.techkuppiapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.json.JSONObject;
import java.util.Collections;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    public String getChatGPTResponse(String prompt) {
        RestTemplate restTemplate = new RestTemplate();

        // Create JSON request body
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", "gpt-4"); // Use GPT-4 or GPT-3.5
        requestJson.put("max_tokens", 100);
        requestJson.put("temperature", 0.7);

        // Set up messages for ChatGPT (System + User prompt)
        requestJson.put("messages", Collections.singletonList(new JSONObject()
                .put("role", "user")
                .put("content", prompt)));

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestJson.toString(), headers);

        // Send request to OpenAI API
        ResponseEntity<String> response = restTemplate.exchange(OPENAI_URL, HttpMethod.POST, entity, String.class);

        // Parse response
        JSONObject jsonResponse = new JSONObject(response.getBody());
        return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }
}