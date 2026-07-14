package com.example.backend.controller;

import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogRecordRepository logRecordRepository;
    private final UserRepository userRepository; // Kullanıcıyı bulmak için eklendi

    public LogController(LogRecordRepository logRecordRepository, UserRepository userRepository) {
        this.logRecordRepository = logRecordRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadLogFile(@RequestParam("file") MultipartFile file, Principal principal) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Hata: Lütfen bir dosya seçin!");

        try {
            User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

            // Döngü dışına bir kere üret, böylece bu dosyadaki her satır aynı ID'ye sahip olur!
            String sessionId = java.util.UUID.randomUUID().toString();

            int savedCount = 0;
            Pattern exceptionPattern = Pattern.compile("\\b(\\w+(?:Exception|Error))\\b");

            try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // ... (logLevel ve exceptionType mantığın buradaydı) ...
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
            return ResponseEntity.ok("Başarılı " + savedCount + " adet log yüklendi. Oturum ID: " + sessionId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<LogRecord>> getAllLogs(Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        return ResponseEntity.ok(logRecordRepository.findByUser(currentUser));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<LogRecord>> getLogsByLevel(@RequestParam("level") String level, Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        return ResponseEntity.ok(logRecordRepository.findByUserAndLogLevel(currentUser, level.toUpperCase()));
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Long>> getLogStats(Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("totalLogs", logRecordRepository.countByUser(currentUser));
        stats.put("errorCount", logRecordRepository.countByUserAndLogLevel(currentUser, "ERROR"));
        stats.put("warnCount", logRecordRepository.countByUserAndLogLevel(currentUser, "WARN"));
        stats.put("infoCount", logRecordRepository.countByUserAndLogLevel(currentUser, "INFO"));
        stats.put("debugCount", logRecordRepository.countByUserAndLogLevel(currentUser, "DEBUG"));

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getUserSessions(Principal principal) {
        try {
            User currentUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
            
            List<LogRecordRepository.SessionInfoProjection> sessions = logRecordRepository.findDistinctSessionsByUser(currentUser);
            
            if (sessions == null || sessions.isEmpty()) {
                System.out.println("Uyarı: Kullanıcı (" + currentUser.getUsername() + ") için oturum bulunamadı.");
            } else {
                System.out.println("Başarılı: " + currentUser.getUsername() + " için " + sessions.size() + " oturum getirildi.");
            }
            
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            System.err.println("Oturumlar getirilirken hata oluştu: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Oturum bilgileri alınamadı: " + e.getMessage());
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<LogRecord>> getLogsBySession(@PathVariable("sessionId") String sessionId, Principal principal) {
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        return ResponseEntity.ok(logRecordRepository.findByUserAndUploadSessionId(currentUser, sessionId));
    }
}