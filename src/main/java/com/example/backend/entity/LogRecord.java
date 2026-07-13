package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String logLevel; // INFO, WARN, ERROR vs.

    @Column(columnDefinition = "TEXT")
    private String message; // Logun tam metni

    private String exceptionType; // Eğer hata satırıyysa NullPointerException vb. buraya gelecek

    private LocalDateTime createdAt = LocalDateTime.now(); // Logun kaydedilme zamanı

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private com.example.backend.entity.User user;
    
    @Column(name = "upload_session_id")
    private String uploadSessionId;
}