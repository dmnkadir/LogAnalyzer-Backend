package com.example.backend.service;

public interface AiProvider {
    String getProviderName();
    String askAi(String prompt);
    String askAi(String systemPrompt, String userPrompt);
    // Spesifik bir model adı göndererek istek atmak için
    String askAi(String systemPrompt, String userPrompt, String specificModel);
}