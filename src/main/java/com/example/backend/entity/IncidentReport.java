package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "incident_reports")
@Data
@NoArgsConstructor
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Raporun hangi oturuma ait olduğunu tutacağız (Birden fazla seçili oturum varsa virgülle ayrılmış ID'ler olabilir)
    @Column(nullable = false, length = 1000)
    private String sessionId;

    // Yapay zekadan dönen o uzun Markdown metnini burada tutacağız
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reportContent;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Raporu üreten kullanıcıyla ilişkilendiriyoruz
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Kullanıcının rapora vereceği özel isim
    @Column(length = 255)
    private String reportName;
}