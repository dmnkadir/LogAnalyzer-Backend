package com.example.backend.dto;

import lombok.Data;

@Data
public class DummyLogRequest {
    private String systemType;    // Örn: Spring Boot, Nginx, PostgreSQL
    private String scenario;      // Örn: NullPointerException, Connection Timeout
    private int minLines;         // Minimum satır sayısı
    private int maxLines;         // Maksimum satır sayısı
    private String customPrompt;  // Kullanıcının eklemek istediği serbest metin
    private String provider; // "gemini" veya "groq"
}