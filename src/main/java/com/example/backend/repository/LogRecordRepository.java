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


    // Keyword (Anahtar Kelime) ile arama algoritması
    @Query("SELECT l FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId = :sessionId AND LOWER(l.message) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<LogRecord> searchLogsByKeyword(@Param("user") User user, @Param("sessionId") String sessionId, @Param("keyword") String keyword);

    // Oturum bazlı seviye (INFO, ERROR vs.) filtresi
    List<LogRecord> findByUserAndUploadSessionIdAndLogLevel(User user, String sessionId, String logLevel);

    // En Çok Görülen Exception
    @Query("SELECT l.exceptionType FROM LogRecord l WHERE l.user = :user AND l.exceptionType IS NOT NULL GROUP BY l.exceptionType ORDER BY COUNT(l.id) DESC LIMIT 1")
    String findMostFrequentException(@Param("user") User user);

    // En Çok Hata Üreten Sınıf (Sadece ERROR seviyesine bakarak)
    @Query("SELECT l.className FROM LogRecord l WHERE l.user = :user AND l.logLevel = 'ERROR' AND l.className IS NOT NULL GROUP BY l.className ORDER BY COUNT(l.id) DESC LIMIT 1")
    String findMostErrorProneClass(@Param("user") User user);

    // İlk ve Son Hata Zamanını Bulma (logTimestamp kullanarak)
    @Query("SELECT MIN(l.logTimestamp) FROM LogRecord l WHERE l.user = :user AND l.logLevel = 'ERROR'")
    java.time.LocalDateTime findFirstErrorTime(@Param("user") User user);

    @Query("SELECT MAX(l.logTimestamp) FROM LogRecord l WHERE l.user = :user AND l.logLevel = 'ERROR'")
    java.time.LocalDateTime findLastErrorTime(@Param("user") User user);
}