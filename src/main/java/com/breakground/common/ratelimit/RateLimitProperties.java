package com.breakground.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 공개 API의 빈도 한도. 단일 서버이므로 메모리에서 계산하며 재시작 시 초기화된다.
 * IP 기준 한도는 회사 공유 IP를 고려해 넉넉하게 두고, 사용자 기준 한도로 개별 남용을 막는다.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(Rule sessionIssue, Rule websocketConnect, Rule message, Rule report,
                                  int roomConnections) {
    public RateLimitProperties {
        if (sessionIssue == null || websocketConnect == null || message == null || report == null
                || roomConnections <= 0) {
            throw new IllegalArgumentException("빈도 제한 설정을 모두 지정해야 합니다.");
        }
    }

    /** window 동안 최대 capacity회. 토큰은 window/capacity 간격으로 하나씩 다시 찬다. */
    public record Rule(int capacity, Duration window) {
        public Rule {
            if (capacity <= 0 || window == null || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("빈도 제한의 횟수와 기간은 양수여야 합니다.");
            }
        }
    }
}
