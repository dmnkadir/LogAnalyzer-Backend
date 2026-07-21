package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository;

    public AiController(AiService aiService, LogRecordRepository logRecordRepository, UserRepository userRepository) {
        this.aiService = aiService;
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> testGemini(@RequestParam("soru") String soru) {
        String cevap = aiService.askAi(soru);
        return ResponseEntity.ok(ApiResponse.success(cevap, "AI Yanıtı başarılı"));
    }

    @GetMapping("/analyze-session/{sessionId}")
    public ResponseEntity<ApiResponse<String>> analyzeSession(@PathVariable String sessionId, Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

            List<LogRecord> criticalLogs = logRecordRepository.findByUserAndUploadSessionId(currentUser, sessionId)
                    .stream()
                    .filter(log -> "ERROR".equals(log.getLogLevel()) || "WARN".equals(log.getLogLevel()))
                    .collect(Collectors.toList());

            if (criticalLogs.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success("Bu oturumda kritik bir hata (ERROR/WARN) bulunamadı. Sistem sağlıklı görünüyor.", "Durum İyi"));
            }

            String logTexts = criticalLogs.stream()
                    .map(LogRecord::getMessage)
                    .collect(Collectors.joining("\n"));

            String prompt = "Aşağıdaki logları analiz et ve bana profesyonel bir olay raporu hazırla. " +
                    "Tüm rapor SADECE TÜRKÇE olmalıdır.\n\n" +
                    "Lütfen raporu aşağıdaki Markdown formatında ver:\n\n" +
                    "### 1. Genel Özet\n" +
                    "[Buraya logların genel durumunu yaz]\n\n" +
                    "### 2. Olası Kök Neden (Root Cause)\n" +
                    "[Buraya hatanın temel sebebini yaz]\n\n" +
                    "### 3. Kritik Hatalar\n" +
                    "[Buraya hataları maddeler halinde yaz]\n\n" +
                    "### 4. Çözüm Adımları\n" +
                    "[Buraya çözüm önerilerini maddeler halinde yaz]\n\n" +
                    "İşte analiz edilecek loglar:\n" + logTexts;

            String aiResponse = aiService.askAi(prompt);
            return ResponseEntity.ok(ApiResponse.success(aiResponse, "Analiz Tamamlandı"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Analiz sırasında bir hata oluştu: " + e.getMessage()));
        }
    }
}