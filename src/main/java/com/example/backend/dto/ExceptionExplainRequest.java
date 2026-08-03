package com.example.backend.dto;

import lombok.Data;

@Data
public class ExceptionExplainRequest {
    private String exceptionName; // Örn: "NullPointerException", "SQLException" vb.
    private String provider; // "gemini" veya "groq"
}