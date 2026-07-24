package com.example.backend.service;

import com.example.backend.dto.DummyLogRequest;
import org.springframework.stereotype.Service;

@Service
public class DummyLogGeneratorService {

    private final AiService aiService;

    public DummyLogGeneratorService(AiService aiService) {
        this.aiService = aiService;
    }

    public String generateDummyLogs(DummyLogRequest request) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Sen kıdemli bir DevOps ve Sistem Yöneticisisin. ");
        promptBuilder.append("Bana ").append(request.getLineCount()).append(" satırlık, ");
        promptBuilder.append(request.getSystemType()).append(" sistemine ait bir log dosyası üret. ");
        promptBuilder.append("Senaryo olarak şunu baz al: ").append(request.getScenario()).append(". ");

        if (request.getCustomPrompt() != null && !request.getCustomPrompt().isEmpty()) {
            promptBuilder.append("Ek detaylar: ").append(request.getCustomPrompt()).append(". ");
        }

        promptBuilder.append("Sadece log satırlarını ver, hiçbir açıklama veya markdown formatı (```) kullanma.");

        // DÜZELTME: askAI yerine askAi (Senin servisine uygun hale getirildi)
        return aiService.askAi(promptBuilder.toString());
    }
}