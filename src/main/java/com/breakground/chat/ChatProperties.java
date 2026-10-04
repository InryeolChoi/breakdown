package com.breakground.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "chat")
public record ChatProperties(Duration messageRetention, int maxBufferedMessages) {
    public ChatProperties {
        if (messageRetention == null || messageRetention.isNegative() || messageRetention.isZero()
                || maxBufferedMessages < 1) {
            throw new IllegalArgumentException("메시지 보관 시간과 용량은 양수여야 합니다.");
        }
    }
}
