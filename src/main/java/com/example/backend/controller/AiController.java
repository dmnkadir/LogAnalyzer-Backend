package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.DummyLogRequest;
import com.example.backend.dto.ExceptionExplainRequest;
import com.example.backend.entity.IncidentReport;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AiService;
import com.example.backend.service.DummyLogGeneratorService;
import com.example.backend.service.IncidentReportService;
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
    private final IncidentReportService incidentReportService;

    public AiController(AiService aiService, LogRecordRepository logRecordRepository, UserRepository userRepository, DummyLogGeneratorService dummyService, IncidentReportService incidentReportService) {
        this.aiService = aiService;
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
        this.dummyService = dummyService;
        this.incidentReportService = incidentReportService;
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> testGemini(
            @RequestParam("soru") String soru,
            @RequestParam(required = false, defaultValue = "gemini-auto") String provider) {
        String cevap = aiService.askAi(soru, provider);
        return ResponseEntity.ok(ApiResponse.success(cevap, "AI Yanıtı başarılı"));
    }

    @GetMapping("/analyze-session/{sessionIds}")
    public ResponseEntity<ApiResponse<String>> analyzeSession(
            @PathVariable List<String> sessionIds,
            @RequestParam(required = false, defaultValue = "gemini-auto") String provider,
            Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

            List<LogRecord> criticalLogs = logRecordRepository.findByUserAndUploadSessionIdIn(currentUser, sessionIds)
                    .stream()
                    .filter(log -> "ERROR".equals(log.getLogLevel()) || "WARN".equals(log.getLogLevel()))
                    .collect(Collectors.toList());

            if (criticalLogs.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success("Seçilen oturumlarda kritik bir hata bulunamadı.", "Durum İyi"));
            }

            String logTexts = criticalLogs.stream().map(LogRecord::getMessage).collect(Collectors.joining("\n"));

            String prompt = "Sen uzman bir DevOps ve Sistem Yöneticisisin. Aşağıdaki logları analiz et ve bana profesyonel bir olay raporu (Incident Report) hazırla. " +
                    "Tüm rapor SADECE TÜRKÇE olmalıdır.\n\n" +
                    "Lütfen raporu AŞAĞIDAKİ MARKDOWN FORMATINA BİREBİR UYARAK ver:\n\n" +
                    "### 1. Genel Özet\n[Özet]\n\n### 2. Risk Seviyesi\n**[KRİTİK, YÜKSEK, ORTA, DÜŞÜK]**\n\n" +
                    "### 3. Olası Kök Neden (Root Cause)\n[Sebep]\n\n### 4. Kritik Hatalar ve Etkilenen Servisler\n" +
                    "- **[Hata Adı]**: [Açıklama]\n\n### 5. Çözüm Adımları\n1. [Adım]\n\nLoglar:\n" + logTexts;

            String aiResponse = aiService.askAi(prompt, provider);

            // --- GÜVENLİK FİLTRESİ (ESNETİLDİ VE TÜRKÇE KARAKTERLERE DUYARLI HALE GETİRİLDİ) ---
            String lowerResponse = aiResponse.toLowerCase();

            // Türkçe karakter bozulmalarını (ö->o, ç->c) tolere etmek için varyasyonları kontrol ediyoruz
            boolean hasSummary = lowerResponse.contains("genel özet") || lowerResponse.contains("genel ozet");
            boolean hasRisk = lowerResponse.contains("risk seviyesi");
            boolean hasSolution = lowerResponse.contains("çözüm adımları") || lowerResponse.contains("cozum adimlari") ||
                    lowerResponse.contains("kök neden") || lowerResponse.contains("kok neden");

            // API'den doğrudan gelen hata mesajları var mı?
            boolean isApiError = lowerResponse.contains("api_error") || lowerResponse.contains("yanıt üretemedi") || lowerResponse.contains("ulaşılamadı");

            // Yapay zeka bazen başlıklardan birini unutabilir veya farklı yazabilir.
            // Eğer isApiError yoksa ve beklediğimiz 3 ana bölümden en az 2'si varsa raporu BAŞARILI sayıyoruz!
            int validHeaders = 0;
            if (hasSummary) validHeaders++;
            if (hasRisk) validHeaders++;
            if (hasSolution) validHeaders++;

            if (isApiError || validHeaders < 2) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Yapay zeka geçerli formatta analiz yapamadı veya bağlantı koptu. Çıktı: " + aiResponse));
            }
            // ---------------------------------------------------------------------------------

            String combinedSessionIds = String.join(",", sessionIds);

            // provider bilgisini de kaydediyoruz
            incidentReportService.saveReport(principal.getName(), combinedSessionIds, aiResponse, provider);

            return ResponseEntity.ok(ApiResponse.success(aiResponse, "Analiz Tamamlandı ve Kaydedildi"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Analiz hatası: " + e.getMessage()));
        }
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<IncidentReport>>> getUserReports(Principal principal) {
        List<IncidentReport> reports = incidentReportService.getUserReports(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(reports, "Geçmiş raporlar başarıyla getirildi"));
    }

    @PostMapping("/generate-dummy")
    public ResponseEntity<ApiResponse<String>> generateDummyLog(@RequestBody DummyLogRequest request, Principal principal) {
        try {
            String newSessionId = dummyService.generateAndSaveDummyLogs(request, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(newSessionId, "Sahte loglar başarıyla üretildi"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Log üretilirken hata: " + e.getMessage()));
        }
    }

    @PostMapping("/explain-exception")
    public ResponseEntity<ApiResponse<String>> explainException(@RequestBody ExceptionExplainRequest request, Principal principal) {
        try {
            if (request.getExceptionName() == null || request.getExceptionName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Exception adı boş olamaz."));
            }

            String prompt = "Sen kıdemli bir Yazılım Mimarı ve Sistem Uzmanısın. Sıkça karşılaştığımız '" + request.getExceptionName() + "' hatasını açıkla. " +
                    "Yanıt SADECE TÜRKÇE olmalıdır.\n\nMARKDOWN FORMATI:\n### Hata Nedir?\n[Açıklama]\n\n### Ne Zaman / Neden Oluşur?\n- [Sebep 1]\n\n### Nasıl Çözülür?\n1. [Çözüm 1]";

            String provider = (request.getProvider() != null) ? request.getProvider() : "gemini";
            String aiResponse = aiService.askAi(prompt, provider);

            return ResponseEntity.ok(ApiResponse.success(aiResponse, "Exception analizi tamamlandı."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Exception açıklanırken hata: " + e.getMessage()));
        }
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<ApiResponse<String>> deleteReport(@PathVariable Long id, Principal principal) {
        try {
            incidentReportService.deleteReport(id, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(null, "Rapor silindi"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Silme hatası: " + e.getMessage()));
        }
    }

    @PutMapping("/reports/{id}/name")
    public ResponseEntity<ApiResponse<String>> updateReportName(@PathVariable Long id, @RequestParam String newName, Principal principal) {
        try {
            incidentReportService.updateReportName(id, principal.getName(), newName);
            return ResponseEntity.ok(ApiResponse.success(null, "Rapor ismi güncellendi"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Hata: " + e.getMessage()));
        }
    }

    @GetMapping("/suggest-name/session/{sessionId}")
    public ResponseEntity<ApiResponse<String>> suggestSessionName(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "gemini-auto") String provider,
            Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
            List<LogRecord> logs = logRecordRepository.findByUserAndUploadSessionId(currentUser, sessionId);

            if (logs.isEmpty()) return ResponseEntity.ok(ApiResponse.success("Bilinmeyen Oturum", "Kayıt yok"));

            String logSnippet = logs.stream().filter(log -> "ERROR".equals(log.getLogLevel()) || "WARN".equals(log.getLogLevel()))
                    .map(LogRecord::getMessage).collect(Collectors.joining("\n"));
            if (logSnippet.isEmpty()) logSnippet = logs.stream().map(LogRecord::getMessage).collect(Collectors.joining("\n"));
            if (logSnippet.length() > 10000) logSnippet = logSnippet.substring(0, 10000);

            String prompt = "Aşağıdaki log kayıtlarını incele. EN KÖK HATAYI belirten en fazla 3-4 kelimelik spesifik bir teknik başlık öner. " +
                    "SADECE BAŞLIĞI YAZ.\n\nLoglar:\n" + logSnippet;

            String aiName = aiService.askAi(prompt, provider).replace("\"", "").trim();

            if (aiName.length() > 60) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Yapay zeka geçerli bir isim üretemedi (Yanıt çok uzun)."));
            }

            return ResponseEntity.ok(ApiResponse.success(aiName, "İsim önerisi başarılı"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Hata oluştu: " + e.getMessage()));
        }
    }
}