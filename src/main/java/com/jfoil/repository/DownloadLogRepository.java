package com.jfoil.repository;

import com.jfoil.model.DownloadLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long> {

    @Query("SELECT COUNT(d) FROM DownloadLog d WHERE d.downloadedAt >= :startDate")
    long countDownloadsSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT d.fileName as fileName, COUNT(d) as count FROM DownloadLog d GROUP BY d.fileName ORDER BY count DESC")
    List<Object[]> findTopDownloadedGames(Pageable pageable);

    @Query("SELECT SUM(d.fileSize) FROM DownloadLog d WHERE d.downloadedAt >= :startDate")
    Long sumBandwidthSince(@Param("startDate") LocalDateTime startDate);
}
