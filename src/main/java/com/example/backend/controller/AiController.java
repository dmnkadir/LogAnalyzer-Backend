package com.example.backend.controller;

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
    public ResponseEntity<String> testGemini(@RequestParam("soru") String soru) {
        String cevap = aiService.askAi(soru);
        return ResponseEntity.ok(cevap);
    }

    @GetMapping("/analyze-session/{sessionId}")
    public ResponseEntity<String> analyzeSession(@PathVariable String sessionId, Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

            // Sadece ilgili oturuma ait HATA ve UYARI loglarını çekiyoruz (Token tasarrufu için INFO'ları eliyoruz)
            List<LogRecord> criticalLogs = logRecordRepository.findByUserAndUploadSessionId(currentUser, sessionId)
                    .stream()
                    .filter(log -> "ERROR".equals(log.getLogLevel()) || "WARN".equals(log.getLogLevel()))
                    .collect(Collectors.toList());

            if (criticalLogs.isEmpty()) {
                return ResponseEntity.ok("Bu oturumda kritik bir hata (ERROR/WARN) bulunamadı. Sistem sağlıklı görünüyor.");
            }

            // Logları tek bir metin bloğu haline getiriyoruz
            String logTexts = criticalLogs.stream()
                    .map(LogRecord::getMessage)
                    .collect(Collectors.joining("\n"));

            // Prompt Engineering: Olay Raporu (Incident Report) formatı dayatıyoruz
            String prompt = "Sen uzman bir DevOps ve Sistem Yöneticisisin. " +
                    "Aşağıdaki logları analiz et ve bana profesyonel bir olay raporu hazırla. " +
                    "ÇOK ÖNEMLİ KURAL: Cevabın %100 düzgün, gramer kurallarına uygun TÜRKÇE olmalıdır. " +
                    "Kesinlikle Vietnamca, İngilizce veya başka bir dilden harf, kelime veya ek kullanma. " +
                    "Uydurma kelimeler veya melez diller yaratma. Sadece standart Türk alfabesini kullan.\n\n" +
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

            // Llama 3.3 modeline gönder ve sonucu dön
            String aiResponse = aiService.askAi(prompt);
            return ResponseEntity.ok(aiResponse);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Analiz sırasında bir hata oluştu: " + e.getMessage());
        }
    }
}