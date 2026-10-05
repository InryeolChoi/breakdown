package com.breakground.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "chat")
public record ChatProperties(Duration reportableDuration, Duration deletionDelay) {
    public ChatProperties {
        if (reportableDuration == null || reportableDuration.isNegative() || reportableDuration.isZero()
                || deletionDelay == null || deletionDelay.isNegative() || deletionDelay.isZero()) {
            throw new IllegalArgumentException("신고 가능 시간과 방 종료 후 삭제 대기 시간은 양수여야 합니다.");
        }
    }
}
