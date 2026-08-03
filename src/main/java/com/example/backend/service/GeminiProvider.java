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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class GeminiProvider implements AiProvider {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public String askAi(String prompt) {
        String defaultSystemPrompt = "Sen uzman bir yazılım mühendisisin. Tüm yanıtlarını TÜRKÇE olarak vermelisin. KESİNLİKLE sadece Latin alfabesi kullan.";
        return askAi(defaultSystemPrompt, prompt);
    }

    @Override
    public String askAi(String systemPrompt, String userPrompt) {
        // Varsayılan olarak en yüksek modele atar
        return askAi(systemPrompt, userPrompt, "gemini-3.6-flash");
    }

    // YENİ: Dinamik Model ve 429 Fallback Yakalayıcısı
    @Override
    public String askAi(String systemPrompt, String userPrompt, String specificModel) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> systemInstructionMap = Map.of("parts", List.of(Map.of("text", systemPrompt)));
            Map<String, Object> userContentMap = Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)));
            Map<String, Object> requestBodyMap = Map.of(
                    "systemInstruction", systemInstructionMap,
                    "contents", List.of(userContentMap),
                    "generationConfig", Map.of("temperature", 0.1)
            );

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBodyMap), headers);

            // URL artık dışarıdan gelen spesifik modele göre dinamik oluşuyor
            String dynamicUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + specificModel + ":generateContent?key=" + apiKey;

            ResponseEntity<String> response = restTemplate.postForEntity(dynamicUrl, request, String.class);

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode candidates = rootNode.path("candidates");

            if (candidates.isArray() && candidates.size() > 0) {
                return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            }
            return "Gemini API yanıt üretemedi.";

        } catch (HttpClientErrorException e) {
            // EĞER KOTA AŞILDIYSA ÖZEL KOD DÖNDÜRÜYORUZ (AiService burayı yakalayacak)
            if (e.getStatusCode().value() == 429) {
                return "API_ERROR_429";
            }
            return "Gemini API Hatası: " + e.getMessage();
        } catch (Exception e) {
            return "Gemini API servisine ulaşılamadı: " + e.getMessage();
        }
    }
}