package com.example.backend.service;

import com.example.backend.dto.LogStatsResponse;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.IncidentReportRepository;
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
    private final IncidentReportRepository incidentReportRepository;

    // Milisaniye toleransı eklendi ve regex'ler güçlendirildi
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b(\\w+(?:Exception|Error))\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)");
    private static final Pattern CLASS_PATTERN = Pattern.compile("([a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)*)\\.([A-Z][a-zA-Z0-9_]*)");

    public LogService(LogRecordRepository logRecordRepository, UserRepository userRepository, IncidentReportRepository incidentReportRepository) {
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
        this.incidentReportRepository = incidentReportRepository;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
    }

    // MERKEZİ LOG AYRIŞTIRICI (Hem AI hem de Dosya Yükleme kullanacak)
    public LogRecord parseLogLine(String line, User currentUser, String sessionId) {
        String logLevel = "INFO";
        if (line.contains("[ERROR]") || line.contains(" ERROR ")) logLevel = "ERROR";
        else if (line.contains("[WARN]") || line.contains(" WARN ")) logLevel = "WARN";
        else if (line.contains("[DEBUG]") || line.contains(" DEBUG ")) logLevel = "DEBUG";

        LogRecord record = new LogRecord();
        record.setLogLevel(logLevel);
        record.setMessage(line.trim());
        record.setUser(currentUser);
        record.setUploadSessionId(sessionId);
        record.setCreatedAt(LocalDateTime.now());

        Matcher exMatcher = EXCEPTION_PATTERN.matcher(line);
        if (exMatcher.find()) {
            record.setExceptionType(exMatcher.group(1));
        }

        Matcher dateMatcher = DATE_PATTERN.matcher(line);
        if (dateMatcher.find()) {
            String dateStr = dateMatcher.group(1).replace("T", " ");
            if (dateStr.contains(".")) { // AI bazen milisaniye üretiyor, parse hatası vermemesi için kesiyoruz
                dateStr = dateStr.substring(0, dateStr.indexOf("."));
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                record.setLogTimestamp(LocalDateTime.parse(dateStr, formatter));
            } catch (DateTimeParseException ignored) {}
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(line);
        if (classMatcher.find()) {
            record.setPackageName(classMatcher.group(1));
            record.setClassName(classMatcher.group(2));
        }

        return record;
    }

    public String processAndSaveLogFile(MultipartFile file, String username) throws Exception {
        User currentUser = getUser(username);
        String sessionId = java.util.UUID.randomUUID().toString();
        int savedCount = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // Gelişmiş ayrıştırıcıyı çağırıyoruz
                LogRecord record = parseLogLine(line, currentUser, sessionId);
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

        String mostFreqEx = null; String mostErrorClass = null; LocalDateTime firstErr = null; LocalDateTime lastErr = null;

        try { mostFreqEx = logRecordRepository.findMostFrequentException(user); } catch (Exception e) {}
        try { mostErrorClass = logRecordRepository.findMostErrorProneClass(user); } catch (Exception e) {}
        try { firstErr = logRecordRepository.findFirstErrorTime(user); } catch (Exception e) {}
        try { lastErr = logRecordRepository.findLastErrorTime(user); } catch (Exception e) {}

        return new LogStatsResponse(total, error, warn, info, debug, mostFreqEx, mostErrorClass, firstErr, lastErr);
    }

    public List<LogRecord> searchAndFilterLogs(String username, String sessionId, String keyword, String level) {
        User user = getUser(username);
        if (keyword != null && !keyword.isEmpty()) {
            return logRecordRepository.searchLogsByKeyword(user, sessionId, keyword);
        } else if (level != null && !level.isEmpty()) {
            return logRecordRepository.findByUserAndUploadSessionIdAndLogLevel(user, sessionId, level.toUpperCase());
        } else {
            return logRecordRepository.findByUserAndUploadSessionId(user, sessionId);
        }
    }

    public List<LogRecordRepository.SessionInfoProjection> getUserSessions(String username) {
        return logRecordRepository.findDistinctSessionsByUser(getUser(username));
    }

    public List<LogRecord> getLogsBySession(String username, String sessionId) {
        return logRecordRepository.findByUserAndUploadSessionId(getUser(username), sessionId);
    }

    public LogStatsResponse getStatsForSessions(String username, List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new LogStatsResponse(0L, 0L, 0L, 0L, 0L, null, null, null, null);
        }
        User user = getUser(username);

        long total = logRecordRepository.countTotalBySessionIds(user, sessionIds);
        long error = logRecordRepository.countByLevelAndSessionIds(user, sessionIds, "ERROR");
        long warn = logRecordRepository.countByLevelAndSessionIds(user, sessionIds, "WARN");
        long info = logRecordRepository.countByLevelAndSessionIds(user, sessionIds, "INFO");
        long debug = logRecordRepository.countByLevelAndSessionIds(user, sessionIds, "DEBUG");

        String mostFreqEx = null; String mostErrorClass = null; LocalDateTime firstErr = null; LocalDateTime lastErr = null;
        try { mostFreqEx = logRecordRepository.findMostFrequentExceptionBySessionIds(user, sessionIds); } catch (Exception e) {}
        try { mostErrorClass = logRecordRepository.findMostErrorProneClassBySessionIds(user, sessionIds); } catch (Exception e) {}
        try { firstErr = logRecordRepository.findFirstErrorTimeBySessionIds(user, sessionIds); } catch (Exception e) {}
        try { lastErr = logRecordRepository.findLastErrorTimeBySessionIds(user, sessionIds); } catch (Exception e) {}

        return new LogStatsResponse(total, error, warn, info, debug, mostFreqEx, mostErrorClass, firstErr, lastErr);
    }

    public List<LogRecord> getFilteredLogs(String username, List<String> sessionIds, String keyword, String level) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return logRecordRepository.findFilteredLogs(getUser(username), sessionIds, keyword, level);
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateSessionName(String username, String sessionId, String newName) {
        User user = getUser(username);
        logRecordRepository.updateSessionName(user, sessionId, newName);

        // Oturumun ismi değiştiyse, ona ait bir Olay Raporu varsa onun da ismini güncelle
        incidentReportRepository.findBySessionIdAndUser(sessionId, user)
                .ifPresent(report -> incidentReportRepository.updateReportName(report.getId(), user, newName));
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteSession(String username, String sessionId) {
        logRecordRepository.deleteByUploadSessionIdAndUser(sessionId, getUser(username));
    }
}