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
public class GroqProvider implements AiProvider {

    @Value("${groq.api.key}")
    private String apiKey;

    private final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroqProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    public String askAi(String prompt) {
        return askAi("Sen uzman bir yazılım mühendisisin.", prompt);
    }

    @Override
    public String askAi(String systemPrompt, String userPrompt) {
        return askAi(systemPrompt, userPrompt, "llama-3.3-70b-versatile");
    }

    // YENİ: Arayüzden gelen zorunlu metot
    @Override
    public String askAi(String systemPrompt, String userPrompt, String specificModel) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBodyMap = Map.of(
                    "model", "llama-3.3-70b-versatile", // Groq için şimdilik sabit
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
            return "Groq API yanıt üretemedi.";
        } catch (Exception e) {
            return "Groq API servisine ulaşılamadı: " + e.getMessage();
        }
    }
}