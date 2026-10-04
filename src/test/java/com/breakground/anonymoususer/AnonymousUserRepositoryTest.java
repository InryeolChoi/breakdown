package com.breakground.anonymoususer;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AnonymousUserRepositoryTest {
    @Autowired
    private AnonymousUserRepository anonymousUserRepository;

    @Autowired
    private AnonymousUserService anonymousUserService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsAnonymousUserWithPostgresEnumStatus() {
        AnonymousUser anonymousUser = new AnonymousUser(
                "BreakgroundAnonymousUserName30",
                LocalDate.of(2026, 10, 3),
                LocalDateTime.of(2026, 10, 3, 14, 0),
                "8f9b3cf2e4a1a5b8e90123456789abcdef0123456789abcdef0123456789abcd"
        );

        AnonymousUser savedUser = anonymousUserRepository.saveAndFlush(anonymousUser);
        entityManager.clear();
        AnonymousUser loadedUser = anonymousUserRepository.findById(savedUser.getId()).orElseThrow();

        assertNotNull(savedUser.getId());
        assertEquals("BreakgroundAnonymousUserName30", loadedUser.getNickname());
        assertEquals(UserStatus.ACTIVE, loadedUser.getStatus());
    }

    @Test
    void generatesKoreanNicknameWhenInputIsBlank() {
        AnonymousUser createdUser = anonymousUserService
                .getOrCreateAnonymousUser(" ", null).user();

        assertTrue(createdUser.getNickname().length() <= 30);
        assertTrue(createdUser.getNickname().matches("[가-힣]+\\d{3}"));
        assertEquals(UserStatus.ACTIVE, createdUser.getStatus());
    }

    @Test
    void revisitUsesTheSameUserAndStoresOnlyTokenHash() {
        AnonymousUserSession first = anonymousUserService.getOrCreateAnonymousUser("오리", null);
        AnonymousUserSession revisit = anonymousUserService.getOrCreateAnonymousUser("다른이름", first.rawToken());

        assertEquals(first.user().getId(), revisit.user().getId());
        assertEquals("오리", revisit.user().getNickname());
        assertEquals(first.rawToken(), revisit.rawToken());
        assertEquals(64, first.user().getTokenHash().length());
        assertNotEquals(first.rawToken(), first.user().getTokenHash());
    }

    @Test
    void expiredUserReceivesANewTokenAndIdentity() {
        AnonymousUserSession first = anonymousUserService.getOrCreateAnonymousUser("오리", null);
        first.user().recordVisit(LocalDateTime.now().minusDays(8));

        AnonymousUserSession renewed = anonymousUserService.getOrCreateAnonymousUser("새오리", first.rawToken());

        assertNotEquals(first.user().getId(), renewed.user().getId());
        assertNotEquals(first.rawToken(), renewed.rawToken());
        assertNotEquals(first.user().getTokenHash(), renewed.user().getTokenHash());
    }

    @Test
    void activeBanDoesNotCreateANewUserEvenWhenIdleTimeExpired() {
        AnonymousUserSession first = anonymousUserService.getOrCreateAnonymousUser("오리", null);
        first.user().recordVisit(LocalDateTime.now().minusDays(8));
        first.user().banForOneWeek(LocalDateTime.now());

        AnonymousUserSession revisit = anonymousUserService.getOrCreateAnonymousUser(null, first.rawToken());

        assertEquals(first.user().getId(), revisit.user().getId());
        assertEquals(UserStatus.BANNED, revisit.user().getStatus());
        assertEquals(first.rawToken(), revisit.rawToken());
    }

    @Test
    void unknownCookieCreatesAFreshToken() {
        AnonymousUserSession session = anonymousUserService
                .getOrCreateAnonymousUser("오리", "unrecognized-token");

        assertNotEquals("unrecognized-token", session.rawToken());
        assertTrue(session.rawToken().matches("[A-Za-z0-9_-]{43}"));
        anonymousUserRepository.flush();
        entityManager.clear();
        assertTrue(anonymousUserRepository.findByTokenHash(session.user().getTokenHash()).isPresent());
    }

    @Test
    void visitTimestampIsUpdatedInPostgres() {
        AnonymousUserSession first = anonymousUserService.getOrCreateAnonymousUser("오리", null);
        LocalDateTime previousVisit = LocalDateTime.now().minusDays(1);
        first.user().recordVisit(previousVisit);
        anonymousUserRepository.flush();
        entityManager.clear();

        anonymousUserService.getOrCreateAnonymousUser(null, first.rawToken());
        anonymousUserRepository.flush();
        entityManager.clear();

        AnonymousUser reloaded = anonymousUserRepository.findById(first.user().getId()).orElseThrow();
        assertTrue(reloaded.getLastSeenAt().isAfter(previousVisit));
    }

    @Test
    void expiredBanIsLiftedAndPersisted() {
        AnonymousUserSession first = anonymousUserService.getOrCreateAnonymousUser("오리", null);
        first.user().banForOneWeek(LocalDateTime.now().minusDays(8));
        anonymousUserRepository.flush();
        entityManager.clear();

        AnonymousUserSession revisit = anonymousUserService.getOrCreateAnonymousUser(null, first.rawToken());
        anonymousUserRepository.flush();
        entityManager.clear();

        AnonymousUser reloaded = anonymousUserRepository.findById(first.user().getId()).orElseThrow();
        assertEquals(first.user().getId(), revisit.user().getId());
        assertEquals(UserStatus.ACTIVE, reloaded.getStatus());
        assertNull(reloaded.getBannedUntil());
    }

    @Test
    void rejectsNicknameLongerThanThirtyCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> anonymousUserService.getOrCreateAnonymousUser("a".repeat(31), null));
    }
}
