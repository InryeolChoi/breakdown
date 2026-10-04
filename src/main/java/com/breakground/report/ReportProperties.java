package com.breakground.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "report")
public record ReportProperties(Duration evidenceRetention) {
    public ReportProperties {
        if (evidenceRetention == null || evidenceRetention.isNegative() || evidenceRetention.isZero()) {
            throw new IllegalArgumentException("신고 증거 보관 시간은 양수여야 합니다.");
        }
    }
}
