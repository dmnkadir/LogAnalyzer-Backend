package com.example.backend.service;

import com.example.backend.dto.LogStatsResponse;
import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.IncidentReportRepository;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class LogService {

    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository;
    private final IncidentReportRepository incidentReportRepository;

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

    public LogRecord parseLogLine(String line, User currentUser, String sessionId) {
        String logLevel = "INFO"; // Varsayılan

        String upperLine = line.toUpperCase();

        if (upperLine.contains("[ERROR]") || upperLine.contains(" ERROR ") || upperLine.startsWith("ERROR") ||
                upperLine.contains("FAILURE") || upperLine.contains("FAILED") || upperLine.contains("ABNORMALLY") || upperLine.contains("FATAL")) {
            logLevel = "ERROR";
        }
        else if (upperLine.contains("[WARN]") || upperLine.contains(" WARN ") || upperLine.startsWith("WARN")) {
            logLevel = "WARN";
        }
        else if (upperLine.contains("[DEBUG]") || upperLine.contains(" DEBUG ") || upperLine.startsWith("DEBUG")) {
            logLevel = "DEBUG";
        }

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
            if (dateStr.contains(".")) {
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

    private int processBufferedReader(BufferedReader br, User currentUser, String sessionId) throws Exception {
        int count = 0;
        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            LogRecord record = parseLogLine(line, currentUser, sessionId);
            logRecordRepository.save(record);
            count++;
        }
        return count;
    }

    // Tekli değil, ÇOKLU dosya alıyor ve TRANSACTIONAL çalışıyor
    @Transactional(rollbackFor = Exception.class)
    public String processAndSaveLogFiles(List<MultipartFile> files, String username) throws Exception {
        User currentUser = getUser(username);
        // Tüm dosyalar aynı oturuma kaydedilecek
        String sessionId = java.util.UUID.randomUUID().toString();
        int totalSavedCount = 0;

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                throw new RuntimeException("Geçersiz dosya adı tespit edildi.");
            }

            String lowerName = originalFilename.toLowerCase();
            int savedCount = 0;

            if (lowerName.endsWith(".zip")) {
                try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                    ZipEntry entry;
                    boolean foundValidEntry = false;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && (entry.getName().toLowerCase().endsWith(".log") || entry.getName().toLowerCase().endsWith(".txt"))) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                            savedCount += processBufferedReader(br, currentUser, sessionId);
                            foundValidEntry = true;
                        }
                        zis.closeEntry();
                    }
                    if (!foundValidEntry) throw new RuntimeException("'" + originalFilename + "' zip arşivi okunabilir bir log dosyası içermiyor!");
                }
            } else if (lowerName.endsWith(".gz")) {
                try (GZIPInputStream gis = new GZIPInputStream(file.getInputStream());
                     BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
                    savedCount += processBufferedReader(br, currentUser, sessionId);
                }
            } else if (lowerName.endsWith(".log") || lowerName.endsWith(".txt")) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    savedCount += processBufferedReader(br, currentUser, sessionId);
                }
            } else {
                throw new RuntimeException("'" + originalFilename + "' desteklenmeyen bir formattır. İşlem iptal edildi.");
            }

            if (savedCount == 0) {
                throw new RuntimeException("'" + originalFilename + "' dosyası içinde geçerli log satırı bulunamadı. İşlem iptal edildi.");
            }

            totalSavedCount += savedCount;
        }

        return "Başarılı! Toplam " + totalSavedCount + " adet log yüklendi. Oturum ID: " + sessionId;
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

    @Transactional
    public void updateSessionName(String username, String sessionId, String newName) {
        User user = getUser(username);
        logRecordRepository.updateSessionName(user, sessionId, newName);

        incidentReportRepository.findFirstBySessionIdAndUserOrderByCreatedAtDesc(sessionId, user)
                .ifPresent(report -> incidentReportRepository.updateReportName(report.getId(), user, newName));
    }

    @Transactional
    public void deleteSession(String username, String sessionId) {
        logRecordRepository.deleteByUploadSessionIdAndUser(sessionId, getUser(username));
    }
}