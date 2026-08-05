package com.example.backend.service;

import com.example.backend.entity.IncidentReport;
import com.example.backend.entity.User;
import com.example.backend.repository.IncidentReportRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;



@Service
public class IncidentReportService {

    private final IncidentReportRepository incidentReportRepository;
    private final UserRepository userRepository;
    private final com.example.backend.repository.LogRecordRepository logRecordRepository;

    public IncidentReportService(IncidentReportRepository incidentReportRepository, UserRepository userRepository, com.example.backend.repository.LogRecordRepository logRecordRepository) {
        this.incidentReportRepository = incidentReportRepository;
        this.userRepository = userRepository;
        this.logRecordRepository = logRecordRepository;
    }

    // AI'dan gelen raporu veritabanına kaydeder
    public IncidentReport saveReport(String username, String sessionId, String reportContent, String provider) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // Aynı oturum ve aynı AI modeli için ikinci kez "Analiz Et" derse günceller, model farklıysa YENİ rapor açar
        IncidentReport report = incidentReportRepository.findFirstBySessionIdAndUserAndProviderOrderByCreatedAtDesc(sessionId, user, provider)
                .orElse(new IncidentReport());

        report.setUser(user);
        report.setSessionId(sessionId);
        report.setReportContent(reportContent);
        report.setProvider(provider); // Model bilgisini kaydediyoruz
        report.setCreatedAt(LocalDateTime.now());

        return incidentReportRepository.save(report);
    }

    // Seçili oturumun önceden üretilmiş bir raporu varsa onu getirir
    public Optional<IncidentReport> getReportBySessionId(String username, String sessionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
        return incidentReportRepository.findFirstBySessionIdAndUserOrderByCreatedAtDesc(sessionId, user);
    }

    // Kullanıcının ürettiği tüm raporları listeler (Frontend'deki Rapor Geçmişi sayfası için)
    public List<IncidentReport> getUserReports(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
        return incidentReportRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteReport(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
        incidentReportRepository.deleteByIdAndUser(id, user);
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateReportName(Long id, String username, String newName) {
        User user = userRepository.findByUsername(username).orElseThrow();

        // Sadece raporun ismini güncelliyoruz
        incidentReportRepository.updateReportName(id, user, newName);

        // Artık rapor ismi değiştiğinde logRecordRepository üzerinden
        // Session (Oturum) ismini GÜNCELLEMİYORUZ. İkisi tamamen bağımsız.
    }

}