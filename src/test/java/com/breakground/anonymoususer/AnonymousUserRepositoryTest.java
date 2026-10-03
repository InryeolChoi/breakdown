package com.breakground.anonymoususer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AnonymousUserRepositoryTest {
    @Autowired
    private AnonymousUserRepository anonymousUserRepository;

    @Autowired
    private AnonymousUserService anonymousUserService;

    @Test
    void savesAndLoadsAnonymousUserWithPostgresEnumStatus() {
        AnonymousUser anonymousUser = new AnonymousUser(
                "BreakgroundAnonymousUserName30",
                LocalDate.of(2026, 10, 3),
                LocalDateTime.of(2026, 10, 3, 14, 0)
        );

        AnonymousUser savedUser = anonymousUserRepository.saveAndFlush(anonymousUser);
        AnonymousUser loadedUser = anonymousUserRepository.findById(savedUser.getId()).orElseThrow();

        assertNotNull(savedUser.getId());
        assertEquals("BreakgroundAnonymousUserName30", loadedUser.getNickname());
        assertEquals(UserStatus.ACTIVE, loadedUser.getStatus());
    }

    @Test
    void generatesKoreanNicknameWhenInputIsBlank() {
        Integer previousMaxId = anonymousUserRepository.findAll().stream()
                .map(AnonymousUser::getId)
                .max(Integer::compareTo)
                .orElse(0);

        anonymousUserService.addNewAnonymousUser(" ");

        AnonymousUser createdUser = anonymousUserRepository.findAll().stream()
                .filter(user -> user.getId() > previousMaxId)
                .findFirst()
                .orElseThrow();

        assertTrue(createdUser.getNickname().length() <= 30);
        assertTrue(createdUser.getNickname().matches("[가-힣]+\\d{3}"));
        assertEquals(UserStatus.ACTIVE, createdUser.getStatus());
    }

    @Test
    void rejectsNicknameLongerThanThirtyCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> anonymousUserService.addNewAnonymousUser("a".repeat(31)));
    }
}
