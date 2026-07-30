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

    public IncidentReportService(IncidentReportRepository incidentReportRepository, UserRepository userRepository) {
        this.incidentReportRepository = incidentReportRepository;
        this.userRepository = userRepository;
    }

    // AI'dan gelen raporu veritabanına kaydeder
    public IncidentReport saveReport(String username, String sessionId, String reportContent) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // Aynı oturumlar için kullanıcı ikinci kez "Analiz Et" derse, eski raporu ezsin (güncellesin)
        IncidentReport report = incidentReportRepository.findBySessionIdAndUser(sessionId, user)
                .orElse(new IncidentReport());

        report.setUser(user);
        report.setSessionId(sessionId);
        report.setReportContent(reportContent);
        report.setCreatedAt(LocalDateTime.now()); // Güncellenmişse tarihini de yenile

        return incidentReportRepository.save(report);
    }

    // Seçili oturumun önceden üretilmiş bir raporu varsa onu getirir
    public Optional<IncidentReport> getReportBySessionId(String username, String sessionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
        return incidentReportRepository.findBySessionIdAndUser(sessionId, user);
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
        incidentReportRepository.updateReportName(id, user, newName);
    }

}