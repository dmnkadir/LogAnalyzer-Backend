package com.example.backend.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final Map<String, AiProvider> providerMap = new HashMap<>();

    public AiService(List<AiProvider> providers) {
        for (AiProvider provider : providers) {
            providerMap.put(provider.getProviderName().toLowerCase(), provider);
        }
    }

    public String askAi(String prompt, String providerName) {
        String defaultSystemPrompt = "Sen uzman bir yazılım mühendisisin. Tüm yanıtlarını TÜRKÇE olarak vermelisin. KESİNLİKLE sadece Latin alfabesi kullan.";
        return askAi(defaultSystemPrompt, prompt, providerName);
    }

    public String askAi(String systemPrompt, String userPrompt, String requestedProvider) {
        if (requestedProvider == null || requestedProvider.trim().isEmpty()) {
            requestedProvider = "gemini-auto";
        }
        requestedProvider = requestedProvider.toLowerCase();

        if (requestedProvider.equals("gemini-auto")) {
            return handleGeminiAutoFallback(systemPrompt, userPrompt);
        }
        else if (requestedProvider.startsWith("gemini-")) {
            String response = providerMap.get("gemini").askAi(systemPrompt, userPrompt, requestedProvider);
            if ("API_ERROR_429".equals(response)) {
                return "HATA: Seçtiğiniz '" + requestedProvider + "' modelinin kotası dolmuş. Lütfen 'Otomatik' modu veya farklı bir modeli seçin.";
            }
            return response;
        }
        // YENİ: OpenRouter üzerinden gelen ücretsiz modelleri yakalıyoruz
        else if (requestedProvider.contains(":free")) {
            return providerMap.get("openrouter").askAi(systemPrompt, userPrompt, requestedProvider);
        }
        else {
            return getProvider(requestedProvider).askAi(systemPrompt, userPrompt);
        }
    }

    private String handleGeminiAutoFallback(String systemPrompt, String userPrompt) {
        // YENİ FALLBACK LİSTESİ: 2.x serisi yerine 3.5 serisini ekledik
        String[] fallbackModels = {"gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite"};
        AiProvider geminiProvider = providerMap.get("gemini");

        for (String model : fallbackModels) {
            String response = geminiProvider.askAi(systemPrompt, userPrompt, model);

            if (!"API_ERROR_429".equals(response)) {
                return response;
            }
            System.out.println("[FALLBACK UYARISI] " + model + " modelinin limiti doldu veya kapalı. Bir sonraki modele geçiliyor...");
        }
        return "Gemini API tüm yedek modellerinde kota aşıldı (429 Too Many Requests). Lütfen Groq modeline geçiş yapın.";
    }

    private AiProvider getProvider(String providerName) {
        AiProvider provider = providerMap.get(providerName);
        if (provider == null) {
            return providerMap.get("gemini");
        }
        return provider;
    }
}