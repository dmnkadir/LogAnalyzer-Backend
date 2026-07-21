package com.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${groq.api.key}")
    private String apiKey;

    // Groq'un API adresini kullanıyoruz
    private final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String askAi(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, String> systemMessage = Map.of(
                    "role", "system",
                    "content", "Sen uzman bir yazılım mühendisisin. Tüm yanıtlarını TÜRKÇE olarak vermelisin. 'Initialize', 'Reference', 'Null' gibi teknik terimleri doğrudan İngilizce olarak bırakabilirsin. KESİNLİKLE sadece Latin alfabesi kullan. Başka alfabeler (Asya dilleri vs.) kullanmak kesinlikle yasaktır."
            );

            Map<String, String> userMessage = Map.of(
                    "role", "user",
                    "content", prompt
            );

            Map<String, Object> requestBodyMap = Map.of(
                    "model", "llama-3.3-70b-versatile", // Gemma kaldırıldığı için Llama 3.3'e dönüyoruz
                    "messages", List.of(systemMessage, userMessage),
                    "temperature", 0.1 // Halüsinasyonları engellemek için yaratıcılığı kıstık
            );

            String requestBody = objectMapper.writeValueAsString(requestBodyMap);

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            // Json ayrıştırma mantığı OpenAI ile tamamen aynı
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            return rootNode.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return "Groq servisine ulaşılamadı: " + e.getMessage();
        }
    }
}