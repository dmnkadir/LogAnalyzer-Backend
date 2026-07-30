package com.example.backend.repository;

import com.example.backend.entity.IncidentReport;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    // Belirli bir oturum için daha önce rapor üretilmiş mi diye bakmak için
    Optional<IncidentReport> findBySessionIdAndUser(String sessionId, User user);

    // Kullanıcının Rapor Geçmişi sayfası için en yeniden en eskiye doğru tüm raporlarını listeler
    List<IncidentReport> findAllByUserOrderByCreatedAtDesc(User user);

    // Rapor ismini güncellemek için özel sorgu
    @Modifying
    @Transactional
    @Query("UPDATE IncidentReport r SET r.reportName = :newName WHERE r.id = :id AND r.user = :user")
    void updateReportName(@Param("id") Long id, @Param("user") User user, @Param("newName") String newName);

    // Sadece raporun sahibi silebilsin diye
    @Transactional
    void deleteByIdAndUser(Long id, User user);
}