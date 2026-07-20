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

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadLogFile(@RequestParam("file") MultipartFile file, Principal principal) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Lütfen bir dosya seçin!"));
        }
        try {
            String message = logService.processAndSaveLogFile(file, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(message, "Dosya başarıyla işlendi"));
        } catch (Exception e) {
            throw new RuntimeException("Dosya işlenirken hata oluştu: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<LogRecord>>> getAllLogs(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getAllLogs(principal.getName()), "Loglar getirildi"));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<LogRecord>>> getLogsByLevel(@RequestParam("level") String level, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getLogsByLevel(principal.getName(), level), "Filtrelenmiş loglar getirildi"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<LogStatsResponse>> getLogStats(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getLogStats(principal.getName()), "İstatistikler getirildi"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<LogRecordRepository.SessionInfoProjection>>> getUserSessions(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getUserSessions(principal.getName()), "Oturumlar getirildi"));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<LogRecord>>> getLogsBySession(@PathVariable("sessionId") String sessionId, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(logService.getLogsBySession(principal.getName(), sessionId), "Oturum logları getirildi"));
    }
}