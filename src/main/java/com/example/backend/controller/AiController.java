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

            // Çok daha yapılandırılmış, Markdown uyumlu ve detaylı
            String prompt = "Sen uzman bir DevOps ve Sistem Yöneticisisin. Aşağıdaki logları analiz et ve bana profesyonel bir olay raporu (Incident Report) hazırla. " +
                    "Tüm rapor SADECE TÜRKÇE olmalıdır.\n\n" +
                    "Lütfen raporu AŞAĞIDAKİ MARKDOWN FORMATINA BİREBİR UYARAK ver:\n\n" +
                    "### 1. Genel Özet\n" +
                    "[Sistemde tam olarak ne yaşandığına dair 2-3 cümlelik net bir özet]\n\n" +
                    "### 2. Risk Seviyesi\n" +
                    "**[Sadece şu kelimelerden BİRİNİ yaz: KRİTİK, YÜKSEK, ORTA, DÜŞÜK]**\n\n" +
                    "### 3. Olası Kök Neden (Root Cause)\n" +
                    "[Hatanın teknik ve temel sebebi]\n\n" +
                    "### 4. Kritik Hatalar ve Etkilenen Servisler\n" +
                    "- **[Hata Adı/Exception]**: [Hatanın açıklaması] *(Etkilenen Sınıf: [Sınıf/Paket adı])*\n" +
                    "- ...\n\n" +
                    "### 5. Çözüm Adımları\n" +
                    "1. [İlk adım]\n" +
                    "2. [İkinci adım]\n\n" +
                    "İşte analiz edilecek kritik loglar:\n" + logTexts;

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