package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.DummyLogRequest;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AiService;
import com.example.backend.service.DummyLogGeneratorService;
import org.springframework.http.HttpStatus;
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
    private final DummyLogGeneratorService dummyService;

    public AiController(AiService aiService, LogRecordRepository logRecordRepository, UserRepository userRepository, DummyLogGeneratorService dummyService) {
        this.aiService = aiService;
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
        this.dummyService = dummyService;
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> testGemini(@RequestParam("soru") String soru) {
        String cevap = aiService.askAi(soru);
        return ResponseEntity.ok(ApiResponse.success(cevap, "AI Yanıtı başarılı"));
    }

    //  Artık PathVariable olarak tek bir String değil, bir List alıyor.
    @GetMapping("/analyze-session/{sessionIds}")
    public ResponseEntity<ApiResponse<String>> analyzeSession(@PathVariable List<String> sessionIds, Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

            // Sadece seçili oturum ID'lerinin içindeki ERROR ve WARN logları getirilir
            List<LogRecord> criticalLogs = logRecordRepository.findByUserAndUploadSessionIdIn(currentUser, sessionIds)
                    .stream()
                    .filter(log -> "ERROR".equals(log.getLogLevel()) || "WARN".equals(log.getLogLevel()))
                    .collect(Collectors.toList());

            if (criticalLogs.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success("Seçilen oturumlarda kritik bir hata (ERROR/WARN) bulunamadı. Sistem sağlıklı görünüyor.", "Durum İyi"));
            }

            String logTexts = criticalLogs.stream()
                    .map(LogRecord::getMessage)
                    .collect(Collectors.joining("\n"));

            String prompt = "Aşağıdaki logları analiz et ve bana profesyonel bir olay raporu (Incident Report) hazırla. " +
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

    @PostMapping("/generate-dummy")
    public ResponseEntity<ApiResponse<String>> generateDummyLog(@RequestBody DummyLogRequest request, Principal principal) {
        try {
            String newSessionId = dummyService.generateAndSaveDummyLogs(request, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(newSessionId, "Sahte loglar başarıyla üretildi ve kaydedildi"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Log üretilirken bir hata oluştu: " + e.getMessage()));
        }
    }
}