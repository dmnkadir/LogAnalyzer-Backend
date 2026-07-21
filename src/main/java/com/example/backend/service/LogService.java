package com.example.backend.service;

import com.example.backend.dto.LogStatsResponse;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogService {

    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository;

    // Kütüphanesiz, raw regex ile kendi ayrıştırıcı (parser) kalıplarımız
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b(\\w+(?:Exception|Error))\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2})");
    // (?:...) yapısı ile grubu yakalamadan sadece paket ve sınıf isimlerini ayıklıyoruz
    private static final Pattern CLASS_PATTERN = Pattern.compile("([a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)*)\\.([A-Z][a-zA-Z0-9_]*)");

    public LogService(LogRecordRepository logRecordRepository, UserRepository userRepository) {
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
    }

    public String processAndSaveLogFile(MultipartFile file, String username) throws Exception {
        User currentUser = getUser(username);
        String sessionId = java.util.UUID.randomUUID().toString();
        int savedCount = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String logLevel = "UNKNOWN";
                if (line.contains("INFO")) logLevel = "INFO";
                else if (line.contains("ERROR")) logLevel = "ERROR";
                else if (line.contains("WARN")) logLevel = "WARN";
                else if (line.contains("DEBUG")) logLevel = "DEBUG";

                LogRecord record = new LogRecord();
                record.setLogLevel(logLevel);
                record.setMessage(line);
                record.setUser(currentUser);
                record.setUploadSessionId(sessionId);

                // Exception Türü Ayıklama
                Matcher exMatcher = EXCEPTION_PATTERN.matcher(line);
                if (exMatcher.find()) {
                    record.setExceptionType(exMatcher.group(1));
                }

                // Zaman Damgası (Timestamp) Ayıklama
                Matcher dateMatcher = DATE_PATTERN.matcher(line);
                if (dateMatcher.find()) {
                    String dateStr = dateMatcher.group(1).replace("T", " "); // Standartlaştırma
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        record.setLogTimestamp(LocalDateTime.parse(dateStr, formatter));
                    } catch (DateTimeParseException ignored) {
                        // Parse edilemezse veritabanına null gider, sistemi çökertmez
                    }
                }

                // Sınıf ve Paket İsmi Ayıklama
                Matcher classMatcher = CLASS_PATTERN.matcher(line);
                if (classMatcher.find()) {
                    record.setPackageName(classMatcher.group(1)); // com.example.service
                    record.setClassName(classMatcher.group(2));   // LogService
                }

                logRecordRepository.save(record);
                savedCount++;
            }
        }
        return "Başarılı " + savedCount + " adet log yüklendi. Oturum ID: " + sessionId;
    }

    public List<LogRecord> getAllLogs(String username) {
        return logRecordRepository.findByUser(getUser(username));
    }

    public List<LogRecord> getLogsByLevel(String username, String level) {
        return logRecordRepository.findByUserAndLogLevel(getUser(username), level.toUpperCase());
    }

    public LogStatsResponse getLogStats(String username) {
        User user = getUser(username);

        long total = logRecordRepository.countByUser(user);
        long error = logRecordRepository.countByUserAndLogLevel(user, "ERROR");
        long warn = logRecordRepository.countByUserAndLogLevel(user, "WARN");
        long info = logRecordRepository.countByUserAndLogLevel(user, "INFO");
        long debug = logRecordRepository.countByUserAndLogLevel(user, "DEBUG");

        // Yeni Detaylı Analiz Verileri
        String mostFreqEx = logRecordRepository.findMostFrequentException(user);
        String mostErrorClass = logRecordRepository.findMostErrorProneClass(user);
        LocalDateTime firstErr = logRecordRepository.findFirstErrorTime(user);
        LocalDateTime lastErr = logRecordRepository.findLastErrorTime(user);

        // Hepsini tek bir DTO içinde paketleyip yolluyoruz
        return new LogStatsResponse(total, error, warn, info, debug, mostFreqEx, mostErrorClass, firstErr, lastErr);
    }

    //  Arama ve Filtreleme
    public List<LogRecord> searchAndFilterLogs(String username, String sessionId, String keyword, String level) {
        User user = getUser(username);

        if (keyword != null && !keyword.isEmpty()) {
            // Kelime araması yapılmışsa
            return logRecordRepository.searchLogsByKeyword(user, sessionId, keyword);
        } else if (level != null && !level.isEmpty()) {
            // Sadece belirli bir seviye (ERROR, WARN) seçilmişse
            return logRecordRepository.findByUserAndUploadSessionIdAndLogLevel(user, sessionId, level.toUpperCase());
        } else {
            // Hiçbir filtre yoksa oturumun tüm loglarını dön
            return logRecordRepository.findByUserAndUploadSessionId(user, sessionId);
        }
    }

    public List<LogRecordRepository.SessionInfoProjection> getUserSessions(String username) {
        return logRecordRepository.findDistinctSessionsByUser(getUser(username));
    }

    public List<LogRecord> getLogsBySession(String username, String sessionId) {
        return logRecordRepository.findByUserAndUploadSessionId(getUser(username), sessionId);
    }
}