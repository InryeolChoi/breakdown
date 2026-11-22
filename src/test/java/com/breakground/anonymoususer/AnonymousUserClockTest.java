package com.breakground.anonymoususer;

import com.breakground.common.ratelimit.RateLimitProperties;
import com.breakground.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnonymousUserClockTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 30);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final RateLimitProperties.Rule RULE = new RateLimitProperties.Rule(100, Duration.ofMinutes(1));
    private static final RateLimitProperties LIMITS = new RateLimitProperties(RULE, RULE, RULE, RULE, 100);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final AnonymousUserRepository repository = mock(AnonymousUserRepository.class);
    private final AnonymousUserService service = new AnonymousUserService(
            repository, mock(KoreanNicknameGenerator.class), clock, new RateLimiter(clock), LIMITS);

    @Test
    void creationUsesTheInjectedClockForDateAndLastVisit() {
        when(repository.save(any(AnonymousUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousUserSession session = service.getOrCreateAnonymousUser("오리", null, "127.0.0.1");

        assertEquals(NOW.toLocalDate(), session.user().getCreatedAt());
        assertEquals(NOW, session.user().getLastSeenAt());
    }

    @Test
    void revisitingJustBeforeSevenDaysKeepsTheTokenAndUpdatesTheVisitUsingTheClock() {
        AnonymousUser existing = existingUser(NOW.minusDays(7).plusNanos(1000));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        AnonymousUserSession session = service.getOrCreateAnonymousUser("새이름", "original-token", "127.0.0.1");

        assertEquals(existing, session.user());
        assertEquals("original-token", session.rawToken());
        assertEquals(NOW, existing.getLastSeenAt());
    }

    @Test
    void revisitingExactlySevenDaysLaterCreatesANewSession() {
        AnonymousUser existing = existingUser(NOW.minusDays(7));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(repository.save(any(AnonymousUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousUserSession session = service.getOrCreateAnonymousUser("새오리", "original-token", "127.0.0.1");

        assertNotEquals(existing, session.user());
        assertNotEquals("original-token", session.rawToken());
        assertEquals(NOW, session.user().getLastSeenAt());
    }

    private AnonymousUser existingUser(LocalDateTime lastSeenAt) {
        return new AnonymousUser("오리", lastSeenAt.toLocalDate(), lastSeenAt, "a".repeat(64));
    }
}
