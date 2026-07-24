package com.example.backend.dto;

import lombok.Data;

@Data
public class DummyLogRequest {
    private String systemType;    // Örn: Spring Boot, Nginx, PostgreSQL
    private String scenario;      // Örn: NullPointerException, Connection Timeout
    private int lineCount;        // Üretilecek log satırı sayısı (Örn: 10)
    private String customPrompt;  // Kullanıcının eklemek istediği serbest metin
}