package com.breakground.common.ratelimit;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 키별 토큰 버킷. 서버 한 대 기준이며 여러 대로 늘리면 공유 저장소가 필요하다. */
@Component
public class RateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String scope, String key, RateLimitProperties.Rule rule) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(scope + ':' + key, k -> new Bucket(rule, now));
        return bucket.tryTake(now);
    }

    public void acquireOrThrow(String scope, String key, RateLimitProperties.Rule rule, String message) {
        if (!tryAcquire(scope, key, rule)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    /** 가득 찬 버킷은 새로 만든 것과 같으므로 지워서 메모리 증가를 막는다. */
    @Scheduled(fixedDelay = 60_000)
    public void evictIdleBuckets() {
        long now = clock.millis();
        buckets.values().removeIf(bucket -> bucket.isFull(now));
    }

    int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private final int capacity;
        private final double refillPerMillis;
        private double tokens;
        private long updatedAt;

        Bucket(RateLimitProperties.Rule rule, long now) {
            this.capacity = rule.capacity();
            this.refillPerMillis = (double) rule.capacity() / rule.window().toMillis();
            this.tokens = rule.capacity();
            this.updatedAt = now;
        }

        synchronized boolean tryTake(long now) {
            refill(now);
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            if (now > updatedAt) {
                tokens = Math.min(capacity, tokens + (now - updatedAt) * refillPerMillis);
                updatedAt = now;
            }
        }
    }
}
