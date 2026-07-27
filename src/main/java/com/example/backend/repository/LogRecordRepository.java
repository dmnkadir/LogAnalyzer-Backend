package com.example.backend.repository;

import com.example.backend.entity.LogRecord;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface LogRecordRepository extends JpaRepository<LogRecord, Long> {

    List<LogRecord> findByUser(User user);
    List<LogRecord> findByUserAndLogLevel(User user, String logLevel);
    long countByUser(User user);
    long countByUserAndLogLevel(User user, String logLevel);
    List<LogRecord> findByUserAndUploadSessionId(User user, String uploadSessionId);

    @Query(value = "SELECT l.upload_session_id AS sessionId, MIN(l.created_at) AS uploadDate, MAX(l.session_name) AS sessionName " +
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

        @com.fasterxml.jackson.annotation.JsonProperty("sessionName")
        String getSessionName();
    }

    @Query("SELECT l FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId = :sessionId AND LOWER(l.message) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<LogRecord> searchLogsByKeyword(@Param("user") User user, @Param("sessionId") String sessionId, @Param("keyword") String keyword);

    List<LogRecord> findByUserAndUploadSessionIdAndLogLevel(User user, String sessionId, String logLevel);

    @Query("SELECT l.exceptionType FROM LogRecord l WHERE l.user = :user AND l.exceptionType IS NOT NULL GROUP BY l.exceptionType ORDER BY COUNT(l.id) DESC LIMIT 1")
    String findMostFrequentException(@Param("user") User user);

    @Query("SELECT l.className FROM LogRecord l WHERE l.user = :user AND l.logLevel = 'ERROR' AND l.className IS NOT NULL GROUP BY l.className ORDER BY COUNT(l.id) DESC LIMIT 1")
    String findMostErrorProneClass(@Param("user") User user);

    @Query("SELECT MIN(l.logTimestamp) FROM LogRecord l WHERE l.user = :user AND l.logLevel = 'ERROR'")
    java.time.LocalDateTime findFirstErrorTime(@Param("user") User user);

    @Query("SELECT MAX(l.logTimestamp) FROM LogRecord l WHERE l.user = :user AND l.logLevel = 'ERROR'")
    java.time.LocalDateTime findLastErrorTime(@Param("user") User user);


    // Birden fazla oturuma ait logları getirme
    @Query("SELECT l FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds")
    List<LogRecord> findByUserAndUploadSessionIdIn(@Param("user") User user, @Param("sessionIds") List<String> sessionIds);

    // Frontend tablosu için çoklu oturum filtreleme (Arama kelimesi ve Seviye)
    @Query("SELECT l FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds " +
            "AND (:keyword IS NULL OR :keyword = '' OR LOWER(l.message) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:level IS NULL OR :level = '' OR l.logLevel = :level)")
    List<LogRecord> findFilteredLogs(@Param("user") User user, @Param("sessionIds") List<String> sessionIds, @Param("keyword") String keyword, @Param("level") String level);

    // İstatistikler: Seviye bazlı sayım
    @Query("SELECT COUNT(l) FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds AND l.logLevel = :level")
    long countByLevelAndSessionIds(@Param("user") User user, @Param("sessionIds") List<String> sessionIds, @Param("level") String level);

    // İstatistikler: Toplam sayım
    @Query("SELECT COUNT(l) FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds")
    long countTotalBySessionIds(@Param("user") User user, @Param("sessionIds") List<String> sessionIds);

    // İstatistikler: En çok görülen Exception
    @Query("SELECT l.exceptionType FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds AND l.exceptionType IS NOT NULL GROUP BY l.exceptionType ORDER BY COUNT(l.id) DESC LIMIT 1")
    String findMostFrequentExceptionBySessionIds(@Param("user") User user, @Param("sessionIds") List<String> sessionIds);

    // İstatistikler: En hatalı sınıf
    @Query("SELECT l.className FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds AND l.logLevel = 'ERROR' AND l.className IS NOT NULL GROUP BY l.className ORDER BY COUNT(l.id) DESC LIMIT 1")
    String findMostErrorProneClassBySessionIds(@Param("user") User user, @Param("sessionIds") List<String> sessionIds);

    // İstatistikler: İlk hata
    @Query("SELECT MIN(l.logTimestamp) FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds AND l.logLevel = 'ERROR'")
    java.time.LocalDateTime findFirstErrorTimeBySessionIds(@Param("user") User user, @Param("sessionIds") List<String> sessionIds);

    // İstatistikler: Son hata
    @Query("SELECT MAX(l.logTimestamp) FROM LogRecord l WHERE l.user = :user AND l.uploadSessionId IN :sessionIds AND l.logLevel = 'ERROR'")
    java.time.LocalDateTime findLastErrorTimeBySessionIds(@Param("user") User user, @Param("sessionIds") List<String> sessionIds);

    @Modifying
    @Transactional
    @Query("UPDATE LogRecord l SET l.sessionName = :newName WHERE l.uploadSessionId = :sessionId AND l.user = :user")
    void updateSessionName(@Param("user") User user, @Param("sessionId") String sessionId, @Param("newName") String newName);

    @Modifying
    @Transactional
    @Query("DELETE FROM LogRecord l WHERE l.uploadSessionId = :sessionId AND l.user = :user")
    void deleteByUploadSessionIdAndUser(@Param("sessionId") String sessionId, @Param("user") User user);

}