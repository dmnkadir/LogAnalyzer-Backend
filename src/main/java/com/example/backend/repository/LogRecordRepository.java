package com.example.backend.repository;

import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogRecordRepository extends JpaRepository<LogRecord, Long> {

    // Sadece istek atan kullanıcının loglarını getirir
    List<LogRecord> findByUser(User user);
    List<LogRecord> findByUserAndLogLevel(User user, String logLevel);

    // Sadece o kullanıcının istatistiklerini hesaplar
    long countByUser(User user);
    long countByUserAndLogLevel(User user, String logLevel);

    // Belirli bir oturuma ve kullanıcıya ait logları getirir
    List<LogRecord> findByUserAndUploadSessionId(User user, String uploadSessionId);

    // Kullanıcıya ait benzersiz oturum ID'lerini ve ilk yükleme tarihlerini (en eski kayıt zamanı) getirir
    @Query(value = "SELECT l.upload_session_id AS sessionId, MIN(l.created_at) AS uploadDate " +
                   "FROM log_records l " +
                   "WHERE l.user_id = :#{#user.id} AND l.upload_session_id IS NOT NULL " +
                   "GROUP BY l.upload_session_id " +
                   "ORDER BY MIN(l.created_at) DESC", 
           nativeQuery = true)
    List<SessionInfoProjection> findDistinctSessionsByUser(@Param("user") User user);

    public interface SessionInfoProjection {
        @com.fasterxml.jackson.annotation.JsonProperty("sessionId")
        String getSessionId();

        @com.fasterxml.jackson.annotation.JsonProperty("uploadDate")
        java.time.LocalDateTime getUploadDate();
    }
}