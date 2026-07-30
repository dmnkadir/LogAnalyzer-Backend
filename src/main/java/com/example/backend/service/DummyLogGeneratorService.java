package com.example.backend.service;

import com.example.backend.dto.DummyLogRequest;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DummyLogGeneratorService {

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
                "5. Logların %70'i INFO/DEBUG gibi normal akış, %30'u ise TAM OLARAK istenen senaryoyla ilgili WARN/ERROR logları olsun.\n" +
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

        // 3. KATMAN: AI'YA YENİ İSTEK AT
        // AiService.java'da hazırladığın o yeni overloaded metodu (systemPrompt, userPrompt) çağırıyoruz
        String aiResponse = aiService.askAi(systemPrompt, userPrompt);

        // Gelen yanıtı satırlara böl ve veritabanına kaydet
        String sessionId = UUID.randomUUID().toString();
        List<LogRecord> logsToSave = new ArrayList<>();

        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            // Artık düz mesaj kaydetmek yok!
            // LogService içindeki o jilet gibi Regex'lerden (parseLogLine) geçirip öyle veritabanına ekliyoruz.
            LogRecord log = logService.parseLogLine(line, user, sessionId);
            logsToSave.add(log);
        }

        logRecordRepository.saveAll(logsToSave);
        return sessionId;

    }
}