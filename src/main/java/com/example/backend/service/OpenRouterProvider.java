package com.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class OpenRouterProvider implements AiProvider {

    @Value("${openrouter.api.key}")
    private String apiKey;

    private final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenRouterProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "openrouter";
    }

    @Override
    public String askAi(String prompt) {
        return askAi("Sen uzman bir yazılım mühendisisin.", prompt, "nvidia/nemotron-3-ultra-4b:free");
    }

    @Override
    public String askAi(String systemPrompt, String userPrompt) {
        return askAi(systemPrompt, userPrompt, "nvidia/nemotron-3-ultra-4b:free");
    }

    @Override
    public String askAi(String systemPrompt, String userPrompt, String specificModel) {
        try {
            if (specificModel == null || specificModel.trim().isEmpty()) {
                specificModel = "nvidia/nemotron-3-ultra-4b:free";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("HTTP-Referer", "http://localhost:8080");
            headers.set("X-Title", "LogAnalyzer");

            Map<String, Object> requestBodyMap = Map.of(
                    "model", specificModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.1
            );

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBodyMap), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BASE_URL, request, String.class);

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode choices = rootNode.path("choices");

            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            return "OpenRouter API yanıt üretemedi.";
        } catch (Exception e) {
            return "OpenRouter API servisine ulaşılamadı: " + e.getMessage();
        }
    }
}