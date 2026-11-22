package com.breakground.anonymoususer;

import com.breakground.common.ratelimit.RateLimitProperties;
import com.breakground.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnonymousUserRateLimitTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final RateLimitProperties.Rule TWO = new RateLimitProperties.Rule(2, Duration.ofMinutes(1));
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final AnonymousUserRepository repository = mock(AnonymousUserRepository.class);
    private final AnonymousUserService service = new AnonymousUserService(repository,
            mock(KoreanNicknameGenerator.class), clock, new RateLimiter(clock),
            new RateLimitProperties(TWO, TWO, TWO, TWO, 10));

    @Test
    void newSessionsArePerIpLimitedWith429() {
        when(repository.save(any(AnonymousUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.getOrCreateAnonymousUser("a", null, "10.0.0.1");
        service.getOrCreateAnonymousUser("b", null, "10.0.0.1");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getOrCreateAnonymousUser("c", null, "10.0.0.1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        service.getOrCreateAnonymousUser("d", null, "10.0.0.2");
    }

    @Test
    void returningUsersWithAValidCookieAreNotCounted() {
        AnonymousUser existing = new AnonymousUser("오리", NOW.toLocalDate(), NOW.minusHours(1), "hash");
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        for (int i = 0; i < 10; i++) {
            assertEquals(existing, service.getOrCreateAnonymousUser(null, "token", "10.0.0.1").user());
        }
    }
}
