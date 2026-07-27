package com.example.backend.service;

import com.example.backend.dto.DummyLogRequest;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DummyLogGeneratorService {

    private final AiService aiService;
    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository;

    // Log ayrıştırmak için özel Regex desenlerimiz
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b(\\w+(?:Exception|Error))\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern CLASS_PATTERN = Pattern.compile("([a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)*)\\.([A-Z][a-zA-Z0-9_]*)");

    public DummyLogGeneratorService(AiService aiService, LogRecordRepository logRecordRepository, UserRepository userRepository) {
        this.aiService = aiService;
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
    }

    public String generateAndSaveDummyLogs(DummyLogRequest request, String username) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Sen kıdemli bir DevOps mühendisisin. ");

        promptBuilder.append("Aşağıdaki kurallara KESİNLİKLE uyarak ").append(request.getMinLines())
                .append(" ile ").append(request.getMaxLines())
                .append(" satır arasında bir ").append(request.getSystemType()).append(" log dosyası üret.\n");

        promptBuilder.append("Hata Senaryosu: ").append(request.getScenario()).append("\n");

        if (request.getCustomPrompt() != null && !request.getCustomPrompt().isEmpty()) {
            promptBuilder.append("Ek Detaylar: ").append(request.getCustomPrompt()).append("\n");
        }

        promptBuilder.append("KURALLAR:\n");
        promptBuilder.append("1. Her satır TAM OLARAK şu formatta başlamalıdır: YYYY-MM-DD HH:mm:ss [SEVİYE] paket.adi.SinifAdi - Mesaj\n");

        // ÖRNEK ÇIKTI FORMATI
        promptBuilder.append("ÖRNEK ÇIKTI FORMATI:\n");
        promptBuilder.append("2024-03-16 14:30:00 [INFO] com.example.system.Bootstrapper - Sistem başlatılıyor...\n");
        promptBuilder.append("2024-03-16 14:30:00 [DEBUG] com.example.system.ConfigLoader - Ayarlar belleğe alındı.\n");
        promptBuilder.append("2024-03-16 14:30:01 [WARN] com.example.db.ConnectionPool - Bağlantı havuzu sınırda, yanıt gecikiyor!\n");
        promptBuilder.append("2024-03-16 14:30:02 [ERROR] com.example.db.QueryRunner - Veritabanı bağlantısı koptu: SQLException\n");

        // DENGELİ DAĞILIM
        promptBuilder.append("2. DİKKAT (DENGELİ DAĞILIM): Bu bir log analiz testidir ancak mantıklı olmalıdır. " +
                "Logların yaklaşık %60'ı sistemin normal çalıştığını gösteren INFO ve DEBUG seviyesinde olmalıdır. " +
                "Kalan %40'lık kısmı ise senaryoya uygun WARN ve ERROR'lara ayır. " +
                "Sadece sürekli ERROR fırlatma! Önce INFO/DEBUG ile başla, sonra WARN'lara geç ve en son ERROR ver. " +
                "ERROR loglarının içinde MUTLAKA 'NullPointerException', 'SQLException', 'TimeoutException' gibi net Java Exception isimleri geçir.\n");

        promptBuilder.append("3. 'paket.adi.SinifAdi' kısmını aynen yazma! Seçilen sisteme uygun GERÇEKÇİ paket ve sınıf isimleri uydur.\n");

        promptBuilder.append("4. Başlangıçta veya sonda HİÇBİR açıklama (İşte loglar vs.) yapma, merhaba deme, markdown (```) KULLANMA. Sadece ham log satırlarını döndür.\n");
        promptBuilder.append("5. Çok uzun sürse bile işlemi yarıda kesme, istenen satır sayısına ulaşana kadar log üretmeye devam et.\n");

        String aiResponse = aiService.askAi(promptBuilder.toString());

        // KULLANICIYI VE YENİ OTURUMU HAZIRLAMA
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
        String sessionId = UUID.randomUUID().toString();

        // GELEN METNİ SATIR SATIR PARSE EDİP VERİTABANINA KAYDETME
        String[] lines = aiResponse.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty() || line.contains("```")) continue;

            // KATI KAPI KONTROLÜ - Satırda tarih deseni yoksa bu bir log değildir, gevezeliktir çöpe at!
            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            if (!dateMatcher.find()) {
                continue;
            }

            String logLevel = "INFO";
            if (line.contains("ERROR") || line.contains("[ERROR]")) logLevel = "ERROR";
            else if (line.contains("WARN") || line.contains("[WARN]")) logLevel = "WARN";
            else if (line.contains("DEBUG") || line.contains("[DEBUG]")) logLevel = "DEBUG";

            LogRecord record = new LogRecord();
            record.setLogLevel(logLevel);
            record.setMessage(line.trim());
            record.setUser(currentUser);
            record.setUploadSessionId(sessionId);

            // Tarihi set etme
            String dateStr = dateMatcher.group(1).replace("T", " ");
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                record.setLogTimestamp(LocalDateTime.parse(dateStr, formatter));
            } catch (DateTimeParseException ignored) {
                record.setLogTimestamp(LocalDateTime.now());
            }

            Matcher exMatcher = EXCEPTION_PATTERN.matcher(line);
            if (exMatcher.find()) {
                record.setExceptionType(exMatcher.group(1));
            }

            Matcher classMatcher = CLASS_PATTERN.matcher(line);
            if (classMatcher.find()) {
                record.setPackageName(classMatcher.group(1));
                record.setClassName(classMatcher.group(2));
            }

            logRecordRepository.save(record);
        }

        return sessionId;
    }
}