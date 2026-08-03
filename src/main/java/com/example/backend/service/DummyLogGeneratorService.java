package com.example.backend.service;

import com.example.backend.dto.DummyLogRequest;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DummyLogGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DummyLogGeneratorService.class);

    private final AiService aiService;
    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository;
    private final LogService logService;

    public DummyLogGeneratorService(AiService aiService, LogRecordRepository logRecordRepository, UserRepository userRepository, LogService logService) {
        this.aiService = aiService;
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
        this.logService = logService;
    }

    public String generateAndSaveDummyLogs(DummyLogRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // 1. KATMAN: MODELİN DNA'SI VE YASAKLAR LİSTESİ (System Prompt)
        String systemPrompt = "Sen kıdemli bir DevOps ve Sistem Uzmanısın. Görevin, SADECE verilen Sistem Tipi ve Hata Senaryosuna %100 sadık kalarak, yüksek detaylı ve gerçekçi sentetik uygulama logları üretmektir.\n\n" +
                "KESİN KURALLAR VE YASAKLAR LİSTESİ (BUNLARA UYMAZSAN SİSTEM ÇÖKER):\n" +
                "1. YASAK_1: İstenen senaryo dışında ASLA farklı bir hata (Exception) tipi üretme! (Örn: HTTP 404 istendiyse NullPointerException, SQLException YAZMA).\n" +
                "2. YASAK_2: SADECE seçilen Sistem Tipine ait paket (package) ve sınıf isimlerini kullan! Başka sistemlere ait log formatlarını karıştırma.\n" +
                "3. YASAK_3: Markdown, HTML, kod bloğu (```) veya 'İşte loglar:' gibi hiçbir açıklama metni KULLANMA. Çıktının ilk karakteri ve son karakteri log verisi olmalıdır.\n" +
                "4. YASAK_4: Logları ASLA birebir kopyala-yapıştır döngüsüne sokma! Her satırda gerçek bir sistemdeki gibi dinamik detaylar (Farklı IP adresleri, farklı Thread ID'ler, farklı SQL sorguları, endpoint'ler veya user-id'ler) uydurarak mesajları ZENGİNLEŞTİR ve ÇEŞİTLENDİR.\n" +
                "5. GERÇEKÇİ DAĞILIM VE ANLATIM AKIŞI:\n" +
                "   - Seviye oranları: INFO ~%45-50, DEBUG ~%25-30, WARN ~%10-15, ERROR ~%10-12.\n" +
                "   - WARN sayısı ERROR sayısından DÜŞÜK OLMAMALI. Gerçek sistemlerde hatalar öncesinde her zaman uyarılar yükselir.\n" +
                "   - Kronolojik bir hikaye anlat: (a) Sistem başlangıcı ve normal çalışma (INFO/DEBUG), (b) İlk belirti uyarıları (WARN), (c) Uyarıların sıklaşması, (d) Hata patlaması (ERROR), (e) Kurtarma/retry denemeleri (INFO/WARN karışık).\n" +
                "   - ERROR loglarını tek bir yere yığma, gerçek bir sistemdeki gibi birkaç küme halinde dağıt ve aralarına retry/recovery INFO logları serpiştir.\n" +
                "6. Format KESİNLİKLE şu olmalı: YYYY-MM-DD HH:mm:ss.SSS [Thread-Name] [SEVİYE] paket.sinif.Adi - Mesaj";

        // 2. KATMAN: KULLANICI PARAMETRELERİ (User Prompt)
        String extraDetails = (request.getCustomPrompt() != null && !request.getCustomPrompt().trim().isEmpty())
                ? request.getCustomPrompt()
                : "Ekstra detay yok, tamamen ana senaryoya ve kurallara odaklan.";

        String userPrompt = String.format(
                "SİSTEM TİPİ: %s\n" +
                        "HATA SENARYOSU: %s\n" +
                        "ÜRETİLECEK SATIR SAYISI: %d\n" +
                        "EKSTRA DETAYLAR: %s\n\n" +
                        "Yukarıdaki parametrelere ve kurallara sıkı sıkıya uyarak sentetik logları üret.",
                request.getSystemType(),
                request.getScenario(),
                request.getMaxLines(),
                extraDetails
        );

        // --- YENİ EKLENEN KISIM: Provider bilgisini alıyoruz ---
        String provider = (request.getProvider() != null && !request.getProvider().trim().isEmpty())
                ? request.getProvider()
                : "gemini";

        // 3. KATMAN: AI'YA YENİ İSTEK AT (Provider eklendi!)
        String aiResponse = aiService.askAi(systemPrompt, userPrompt, provider);
        // -------------------------------------------------------

        // ===== GÜVENLİK KONTROLÜ 1: Boş veya hata yanıtı mı? =====
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            throw new RuntimeException("AI boş yanıt döndü. Gemini API yanıt üretemedi.");
        }
        if (aiResponse.contains("API servisine ulaşılamadı") || aiResponse.contains("kota aşıldı") || aiResponse.contains("Hatası:")) {
            throw new RuntimeException("AI servisi hata döndü: " + aiResponse);
        }

        // ===== GÜVENLİK KONTROLÜ 2: Markdown kod bloğu temizleme =====
        // Gemini yanıtı ```log ... ``` veya ```\n...\n``` ile sarabiliyor
        String cleanedResponse = aiResponse.replaceAll("(?m)^```[a-zA-Z]*\\s*$", "").trim();

        // Gelen yanıtı satırlara böl ve veritabanına kaydet
        String sessionId = UUID.randomUUID().toString();
        List<LogRecord> logsToSave = new ArrayList<>();

        String[] lines = cleanedResponse.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            LogRecord record = logService.parseLogLine(line, user, sessionId);
            logsToSave.add(record);
        }

        // ===== GÜVENLİK KONTROLÜ 3: 0 log parse edildiyse hata fırlat =====
        if (logsToSave.isEmpty()) {
            log.error("[DummyLogGenerator] 0 log parse edildi! Ham AI yanıtı: {}", aiResponse);
            throw new RuntimeException("AI yanıtından hiç log satırı parse edilemedi. AI'ın döndüğü ham yanıt sunucu loglarına kaydedildi.");
        }

        logRecordRepository.saveAll(logsToSave);
        log.info("[DummyLogGenerator] {} adet log başarıyla kaydedildi. Session: {}", logsToSave.size(), sessionId);
        return sessionId;
    }
}