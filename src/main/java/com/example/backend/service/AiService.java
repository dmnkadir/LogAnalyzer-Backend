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
        // YENİ: JSON dosyasındaki geçerli ve desteklenen latest modelleri kullanıyoruz
        String[] fallbackModels = {"gemini-flash-latest", "gemini-pro-latest", "gemini-flash-lite-latest"};
        AiProvider geminiProvider = providerMap.get("gemini");

        for (String model : fallbackModels) {
            String response = geminiProvider.askAi(systemPrompt, userPrompt, model);

            if (!"API_ERROR_429".equals(response) && !response.startsWith("Gemini API Hatası")) {
                return response;
            }
            System.out.println("[FALLBACK UYARISI] " + model + " modelinde hata alındı. Bir sonraki modele geçiliyor...");
        }
        return "Gemini API tüm yedek modellerinde hata aldı veya kota aşıldı. Lütfen Groq modeline geçiş yapın.";
    }

    private AiProvider getProvider(String providerName) {
        AiProvider provider = providerMap.get(providerName);
        if (provider == null) {
            return providerMap.get("gemini");
        }
        return provider;
    }
}