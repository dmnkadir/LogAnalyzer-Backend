package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogStatsResponse {
    private long totalLogs;
    private long errorCount;
    private long warnCount;
    private long infoCount;
    private long debugCount;


    private String mostFrequentException;
    private String mostErrorProneClass;
    private LocalDateTime firstErrorTime;
    private LocalDateTime lastErrorTime;
}