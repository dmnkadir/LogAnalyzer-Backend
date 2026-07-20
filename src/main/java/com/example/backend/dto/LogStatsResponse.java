package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogStatsResponse {
    private long totalLogs;
    private long errorCount;
    private long warnCount;
    private long infoCount;
    private long debugCount;
}