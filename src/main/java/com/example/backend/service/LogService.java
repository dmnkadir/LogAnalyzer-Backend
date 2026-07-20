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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogService {

    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository;

    public LogService(LogRecordRepository logRecordRepository, UserRepository userRepository) {
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
    }

    // Ortak kullanıcı bulma metodu (Bulamazsa bizim GlobalExceptionHandler yakalayacak)
    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
    }

    // Dosya okuma ve veritabanına kaydetme iş mantığı
    public String processAndSaveLogFile(MultipartFile file, String username) throws Exception {
        User currentUser = getUser(username);
        String sessionId = java.util.UUID.randomUUID().toString();
        int savedCount = 0;
        Pattern exceptionPattern = Pattern.compile("\\b(\\w+(?:Exception|Error))\\b");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String logLevel = "UNKNOWN";
                String exceptionType = null;
                if (line.contains("INFO")) logLevel = "INFO";
                else if (line.contains("ERROR")) logLevel = "ERROR";
                else if (line.contains("WARN")) logLevel = "WARN";
                else if (line.contains("DEBUG")) logLevel = "DEBUG";

                Matcher matcher = exceptionPattern.matcher(line);
                if (matcher.find()) exceptionType = matcher.group(1);

                LogRecord record = new LogRecord();
                record.setLogLevel(logLevel);
                record.setMessage(line);
                record.setExceptionType(exceptionType);
                record.setUser(currentUser);
                record.setUploadSessionId(sessionId);

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

        return new LogStatsResponse(total, error, warn, info, debug);
    }

    public List<LogRecordRepository.SessionInfoProjection> getUserSessions(String username) {
        return logRecordRepository.findDistinctSessionsByUser(getUser(username));
    }

    public List<LogRecord> getLogsBySession(String username, String sessionId) {
        return logRecordRepository.findByUserAndUploadSessionId(getUser(username), sessionId);
    }
}