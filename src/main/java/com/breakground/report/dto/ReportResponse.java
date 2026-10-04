package com.breakground.report.dto;

import com.breakground.report.Report;
import java.time.LocalDateTime;

public record ReportResponse(Integer id, LocalDateTime createdAt) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getCreatedAt());
    }
}
