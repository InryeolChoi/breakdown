package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.anonymoususer.AnonymousUserRepository;
import com.breakground.report.Report;
import com.breakground.report.ReportRepository;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class MessageRepositoryTest {
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 6, 10, 0);
    private static final LocalDateTime EXPIRES_AT = SENT_AT.plusMinutes(15);
    private static final LocalDateTime DELETE_AFTER = SENT_AT.withHour(11).withMinute(40);

    @Autowired
    private MessageRepository messages;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private AnonymousUserRepository users;
    @Autowired
    private ReportRepository reports;
    @Autowired
    private EntityManager entityManager;

    private Room room;
    private AnonymousUser author;
    private AnonymousUser reporter;

    @BeforeEach
    void prepareRoomAndUsers() {
        room = rooms.saveAndFlush(new Room("매핑테스트방", LocalTime.of(10, 0),
                LocalTime.of(11, 30), true, true));
        author = users.saveAndFlush(new AnonymousUser("작성자", SENT_AT.toLocalDate(),
                SENT_AT, tokenHash()));
        reporter = users.saveAndFlush(new AnonymousUser("신고자", SENT_AT.toLocalDate(),
                SENT_AT, tokenHash()));
    }

    @Test
    void generatesUuidAndReloadsMessageWithItsAuthorAndRoom() {
        Message message = new Message(room, author, "😀".repeat(140),
                SENT_AT, EXPIRES_AT, DELETE_AFTER);
        assertNull(message.getId());
        Message saved = messages.saveAndFlush(message);
        UUID id = saved.getId();
        Integer roomId = room.getId();
        Integer authorId = author.getId();

        entityManager.clear();
        Message loaded = messages.findById(id).orElseThrow();

        assertNotNull(id);
        assertEquals(roomId, loaded.getRoom().getId());
        assertEquals(authorId, loaded.getAuthor().getId());
        assertEquals("작성자", loaded.getNickname());
        assertEquals("😀".repeat(140), loaded.getContent());
        assertEquals(SENT_AT, loaded.getSentAt());
        assertEquals(EXPIRES_AT, loaded.getExpiresAt());
        assertEquals(DELETE_AFTER, loaded.getDeleteAfter());
    }

    @Test
    void expiredMessageIsNotReportableEvenWhileTheRowRemains() {
        Message saved = messages.saveAndFlush(new Message(room, author, "점심 메뉴",
                SENT_AT, EXPIRES_AT, DELETE_AFTER));
        UUID id = saved.getId();
        entityManager.clear();

        assertTrue(messages.findByIdAndExpiresAtGreaterThan(id, EXPIRES_AT.minusNanos(1000)).isPresent());
        assertTrue(messages.findByIdAndExpiresAtGreaterThan(id, EXPIRES_AT).isEmpty());
        assertTrue(messages.findByIdAndExpiresAtGreaterThan(id, EXPIRES_AT.plusSeconds(1)).isEmpty());
        assertTrue(messages.findById(id).isPresent());
    }

    @Test
    void cleanupUsesDeletionTimeRatherThanReportExpiration() {
        Message saved = messages.saveAndFlush(new Message(room, author, "점심 메뉴",
                SENT_AT, EXPIRES_AT, DELETE_AFTER));
        UUID id = saved.getId();
        entityManager.clear();

        assertEquals(0, messages.deleteReadyForCleanup(EXPIRES_AT));
        assertEquals(0, messages.deleteReadyForCleanup(DELETE_AFTER.minusNanos(1000)));
        assertEquals(1, messages.deleteReadyForCleanup(DELETE_AFTER));
        entityManager.clear();
        assertTrue(messages.findById(id).isEmpty());
    }

    @Test
    void deletingOriginalMessagePreservesReportEvidenceAndDuplicateKey() {
        Message saved = messages.saveAndFlush(new Message(room, author, "신고 증거 원문",
                SENT_AT, EXPIRES_AT, DELETE_AFTER));
        ChatMessage evidence = new ChatMessage(saved.getId(), room.getId(), author.getId(),
                saved.getNickname(), saved.getContent(), saved.getSentAt(), saved.getExpiresAt());
        LocalDateTime reportedAt = SENT_AT.plusMinutes(10);
        Report report = reports.saveAndFlush(new Report(reporter, author, room, evidence,
                "테스트 신고", reportedAt, reportedAt.plusDays(30)));
        Integer reportId = report.getId();
        Integer reporterId = reporter.getId();
        UUID messageId = saved.getId();

        assertEquals(1, messages.deleteReadyForCleanup(DELETE_AFTER));
        entityManager.clear();

        assertTrue(messages.findById(messageId).isEmpty());
        Report loaded = reports.findById(reportId).orElseThrow();
        assertEquals(messageId, loaded.getMessageEventId());
        assertEquals("신고 증거 원문", loaded.getEvidenceContent());
        assertEquals(reportedAt.plusDays(30), loaded.getEvidenceExpiresAt());
        assertTrue(reports.existsByReporter_IdAndMessageEventId(reporterId, messageId));
    }

    private static String tokenHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
