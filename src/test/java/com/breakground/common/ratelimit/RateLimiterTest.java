package com.breakground.common.ratelimit;

import com.breakground.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);
    private static final RateLimitProperties.Rule FIVE_PER_10S = new RateLimitProperties.Rule(5, Duration.ofSeconds(10));
    private final MutableClock clock = new MutableClock(NOW);
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("message", "1", FIVE_PER_10S));
        }
        assertFalse(limiter.tryAcquire("message", "1", FIVE_PER_10S));
    }

    @Test
    void refillsOneTokenPerIntervalAndKeepsKeysAndScopesSeparate() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("message", "1", FIVE_PER_10S);
        }
        assertTrue(limiter.tryAcquire("message", "2", FIVE_PER_10S));
        assertTrue(limiter.tryAcquire("report", "1", FIVE_PER_10S));

        clock.set(NOW.plusNanos(Duration.ofMillis(1999).toNanos()));
        assertFalse(limiter.tryAcquire("message", "1", FIVE_PER_10S));
        clock.set(NOW.plusSeconds(2));
        assertTrue(limiter.tryAcquire("message", "1", FIVE_PER_10S));
        assertFalse(limiter.tryAcquire("message", "1", FIVE_PER_10S));
    }

    @Test
    void evictsOnlyBucketsThatHaveFullyRefilled() {
        limiter.tryAcquire("message", "1", FIVE_PER_10S);
        clock.set(NOW.plusSeconds(1));
        limiter.tryAcquire("message", "2", FIVE_PER_10S);

        clock.set(NOW.plusSeconds(2));
        limiter.evictIdleBuckets();
        assertEquals(1, limiter.size());

        clock.set(NOW.plusSeconds(20));
        limiter.evictIdleBuckets();
        assertEquals(0, limiter.size());
    }
}
