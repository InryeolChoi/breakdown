package com.breakground.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, Integer> {
    boolean existsByReporter_IdAndMessageEventId(Integer reporterId, UUID messageEventId);

    @Modifying
    @Query("delete from Report r where r.evidenceExpiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
