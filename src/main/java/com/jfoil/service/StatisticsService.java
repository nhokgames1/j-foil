package com.jfoil.service;

import com.jfoil.repository.DownloadLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final DownloadLogRepository logRepository;

    public StatisticsService(DownloadLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public long getDownloadsToday() {
        return logRepository.countDownloadsSince(LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT));
    }

    public long getDownloadsThisMonth() {
        return logRepository.countDownloadsSince(LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIDNIGHT));
    }

    public String getBandwidthTodayFormatted() {
        Long bytes = logRepository.sumBandwidthSince(LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT));
        return formatBytes(bytes);
    }

    public String getBandwidthThisMonthFormatted() {
        Long bytes = logRepository.sumBandwidthSince(LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIDNIGHT));
        return formatBytes(bytes);
    }
    
    public List<Map<String, Object>> getTopGames() {
        List<Object[]> results = logRepository.findTopDownloadedGames(PageRequest.of(0, 5));
        return results.stream()
                .map(row -> Map.of("fileName", row[0], "count", row[1]))
                .collect(Collectors.toList());
    }

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
