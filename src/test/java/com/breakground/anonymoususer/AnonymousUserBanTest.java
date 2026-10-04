package com.breakground.anonymoususer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousUserBanTest {
    @Test
    void banExpiresExactlySevenDaysAfterItStarts() {
        LocalDateTime bannedAt = LocalDateTime.of(2026, 10, 4, 12, 0);
        AnonymousUser user = createUser();

        user.banForOneWeek(bannedAt);

        assertEquals(UserStatus.BANNED, user.getStatus());
        assertEquals(bannedAt.plusDays(7), user.getBannedUntil());
        assertTrue(user.isBanActiveAt(bannedAt.plusDays(6).plusHours(23)));
        assertFalse(user.isBanActiveAt(bannedAt.plusDays(7)));
    }

    @Test
    void expiredBanReturnsUserToActive() {
        LocalDateTime bannedAt = LocalDateTime.of(2026, 10, 4, 12, 0);
        AnonymousUser user = createUser();
        user.banForOneWeek(bannedAt);

        user.liftBanIfExpired(bannedAt.plusDays(7));

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getBannedUntil());
    }

    @Test
    void banIsNotLiftedBeforeItsDeadline() {
        LocalDateTime bannedAt = LocalDateTime.of(2026, 10, 5, 0, 0);
        AnonymousUser user = createUser();
        user.banForOneWeek(bannedAt);

        user.liftBanIfExpired(bannedAt.plusDays(7).minusNanos(1));

        assertEquals(UserStatus.BANNED, user.getStatus());
        assertTrue(user.isBanActiveAt(bannedAt.plusDays(7).minusNanos(1)));
    }

    private AnonymousUser createUser() {
        return new AnonymousUser(
                "익명123",
                LocalDate.of(2026, 10, 4),
                LocalDateTime.of(2026, 10, 4, 12, 0),
                "8f9b3cf2e4a1a5b8e90123456789abcdef0123456789abcdef0123456789abcd"
        );
    }
}
