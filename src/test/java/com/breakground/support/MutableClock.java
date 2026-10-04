package com.breakground.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class MutableClock extends Clock {
    private volatile Instant current;
    private final ZoneId zone;
    public MutableClock(LocalDateTime now) {
        this.zone = ZoneId.of("Asia/Seoul");
        set(now);
    }
    public void set(LocalDateTime now) { current = now.atZone(zone).toInstant(); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId newZone) { return Clock.fixed(current, newZone); }
    @Override public Instant instant() { return current; }
}
