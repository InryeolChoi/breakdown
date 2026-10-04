package com.breakground.report;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class ReportCleanup {
    private final ReportRepository reports;
    private final Clock clock;
    public ReportCleanup(ReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${report.cleanup-interval-ms:60000}")
    @Transactional
    public void removeExpired() {
        reports.deleteExpired(LocalDateTime.now(clock));
    }
}
