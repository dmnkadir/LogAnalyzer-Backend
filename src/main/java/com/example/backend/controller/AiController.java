package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.DummyLogRequest;
import com.example.backend.dto.ExceptionExplainRequest;
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

    // Tekli Exception Açıklama Servisi
    @PostMapping("/explain-exception")
    public ResponseEntity<ApiResponse<String>> explainException(@RequestBody ExceptionExplainRequest request, Principal principal) {
        try {
            if (request.getExceptionName() == null || request.getExceptionName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Exception adı boş olamaz."));
            }

            // AI için özel, süslü bir Markdown şablonu hazırlıyoruz
            String prompt = "Sen kıdemli bir Yazılım Mimarı ve Sistem Uzmanısın. " +
                    "Sistem loglarında sıkça karşılaştığımız '" + request.getExceptionName() + "' hatasını yazılım ekibine açıklaman gerekiyor. " +
                    "Tüm yanıtın SADECE TÜRKÇE olmalıdır. Teknik terimleri (örn: Database, Memory) İngilizce bırakabilirsin.\n\n" +
                    "Lütfen cevabını SADECE aşağıdaki MARKDOWN formatına BİREBİR uyarak ver (Başka hiçbir giriş cümlesi kurma):\n\n" +
                    "### Hata Nedir?\n" +
                    "[Hatanın ne anlama geldiğine dair kısa ve net bir açıklama]\n\n" +
                    "### Ne Zaman / Neden Oluşur?\n" +
                    "- [En yaygın 1. sebep]\n" +
                    "- [En yaygın 2. sebep]\n" +
                    "- [Varsa 3. sebep]\n\n" +
                    "### Nasıl Çözülür?\n" +
                    "1. [İlk ve en etkili çözüm adımı]\n" +
                    "2. [Alternatif veya ikinci kontrol adımı]\n\n" +
                    "ÖNEMLİ: Hata isimlerini ve teknik terimleri mutlaka **kalın** (bold) yaz.";

            String aiResponse = aiService.askAi(prompt);
            return ResponseEntity.ok(ApiResponse.success(aiResponse, "Exception analizi başarıyla tamamlandı."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Exception açıklanırken bir hata oluştu: " + e.getMessage()));
        }
    }
}