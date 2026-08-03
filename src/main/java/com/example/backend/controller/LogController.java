package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.LogStatsResponse;
import com.example.backend.entity.LogRecord;
import com.example.backend.repository.LogRecordRepository;
import com.example.backend.service.LogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    // Artık çoklu dosya (List<MultipartFile> files) kabul ediyor
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadLogFiles(@RequestParam("files") List<MultipartFile> files, Principal principal) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Lütfen en az bir dosya seçin!"));
        }
        try {
            String message = logService.processAndSaveLogFiles(files, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(message, "Dosyalar başarıyla işlendi"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<LogRecord>>> getAllLogs(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getAllLogs(principal.getName()), "Loglar getirildi"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<LogStatsResponse>> getLogStats(@RequestParam(required = false) List<String> sessionIds, Principal principal) {
        if (sessionIds != null && !sessionIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(logService.getStatsForSessions(principal.getName(), sessionIds), "Seçili oturum istatistikleri getirildi"));
        }
        return ResponseEntity.ok(ApiResponse.success(logService.getLogStats(principal.getName()), "Genel istatistikler getirildi"));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<LogRecord>>> getFilteredLogs(
            @RequestParam(required = false) List<String> sessionIds,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            Principal principal) {

        List<LogRecord> logs = logService.getFilteredLogs(principal.getName(), sessionIds, keyword, level);
        return ResponseEntity.ok(ApiResponse.success(logs, "Filtrelenmiş loglar getirildi"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<LogRecordRepository.SessionInfoProjection>>> getUserSessions(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getUserSessions(principal.getName()), "Oturumlar getirildi"));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<LogRecord>>> getLogsBySession(
            @PathVariable String sessionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            Principal principal) {
        try {
            List<LogRecord> logs = logService.searchAndFilterLogs(principal.getName(), sessionId, keyword, level);
            return ResponseEntity.ok(ApiResponse.success(logs, "Loglar başarıyla getirildi"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Loglar getirilirken hata oluştu: " + e.getMessage()));
        }
    }

    @PutMapping("/session/{sessionId}/name")
    public ResponseEntity<ApiResponse<String>> updateSessionName(
            @PathVariable String sessionId,
            @RequestParam String newName,
            Principal principal) {
        logService.updateSessionName(principal.getName(), sessionId, newName);
        return ResponseEntity.ok(ApiResponse.success(null, "Oturum ismi başarıyla güncellendi"));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<String>> deleteSession(
            @PathVariable String sessionId,
            Principal principal) {
        logService.deleteSession(principal.getName(), sessionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Oturum ve ait olduğu tüm loglar başarıyla silindi"));
    }
}