package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUserRepository;
import com.breakground.anonymoususer.AnonymousUserService;
import com.breakground.anonymoususer.AnonymousUserSession;
import com.breakground.report.Report;
import com.breakground.report.ReportRepository;
import com.breakground.report.ReportService;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import com.breakground.support.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// 클래스에 @Transactional을 붙이지 않고 실제 커밋/롤백을 검증한다.
@SpringBootTest(properties = {"chat.cleanup-interval-ms=3600000", "report.cleanup-interval-ms=3600000"})
class ChatPersistenceTransactionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 10, 30);

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock transactionTestClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired ChatService chat;
    @Autowired ReportService reportService;
    @Autowired MessageRepository messages;
    @Autowired ReportRepository reports;
    @Autowired AnonymousUserService sessions;
    @Autowired AnonymousUserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;

    private TransactionTemplate transactions;
    private Room room;
    private AnonymousUserSession author;
    private AnonymousUserSession reporter;

    @BeforeEach
    void prepareCommittedFixtures() {
        transactions = new TransactionTemplate(transactionManager);
        clock.set(NOW);
        transactions.executeWithoutResult(status -> {
            room = rooms.saveAndFlush(new Room("커밋테스트방", LocalTime.of(10, 0),
                    LocalTime.of(11, 0), true, true));
            author = sessions.getOrCreateAnonymousUser("작성자", null);
            reporter = sessions.getOrCreateAnonymousUser("신고자", null);
            author.user().recordVisit(NOW.minusHours(1));
            reporter.user().recordVisit(NOW.minusHours(1));
        });
    }

    @AfterEach
    void removeOnlyThisTestsCommittedFixtures() {
        if (room == null) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Report r where r.room.id = :roomId")
                    .setParameter("roomId", room.getId()).executeUpdate();
            entityManager.createQuery("delete from Message m where m.room.id = :roomId")
                    .setParameter("roomId", room.getId()).executeUpdate();
            users.deleteAllById(List.of(author.user().getId(), reporter.user().getId()));
            rooms.deleteById(room.getId());
        });
    }

    @Test
    void successfulSendCommitsBothMessageAndVisitTime() {
        ChatMessage response = chat.create(room.getId(), author.rawToken(), "  DB 원문  ");

        transactions.executeWithoutResult(status -> {
            Message loaded = messages.findById(response.id()).orElseThrow();
            assertEquals("DB 원문", loaded.getContent());
            assertEquals(author.user().getId(), loaded.getAuthor().getId());
            assertEquals(NOW, users.findById(author.user().getId()).orElseThrow().getLastSeenAt());
            assertEquals(NOW.withHour(11).withMinute(10), loaded.getDeleteAfter());
        });
    }

    @Test
    void rollbackRemovesMessageAndRestoresVisitTime() {
        UUID id = transactions.execute(status -> {
            ChatMessage response = chat.create(room.getId(), author.rawToken(), "롤백 대상");
            messages.flush();
            status.setRollbackOnly();
            return response.id();
        });

        assertFalse(messages.existsById(id));
        assertEquals(NOW.minusHours(1), users.findById(author.user().getId()).orElseThrow().getLastSeenAt());
    }

    @Test
    void otherTransactionCannotReadMessageBeforeCommit() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            UUID id = transactions.execute(status -> {
                ChatMessage response = chat.create(room.getId(), author.rawToken(), "커밋 전 비공개");
                messages.flush();
                try {
                    boolean visible = executor.submit(() -> messages.existsById(response.id()))
                            .get(10, TimeUnit.SECONDS);
                    assertFalse(visible);
                } catch (Exception e) {
                    throw new IllegalStateException("별도 트랜잭션 조회 검증 실패", e);
                }
                return response.id();
            });
            assertTrue(messages.existsById(id));
        }
    }

    @Test
    void invalidSendRollsBackVisitUpdate() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> chat.create(room.getId(), author.rawToken(), " "));
        assertEquals(400, error.getStatusCode().value());
        assertEquals(NOW.minusHours(1), users.findById(author.user().getId()).orElseThrow().getLastSeenAt());
    }

    @Test
    void committedEvidenceCanBeReportedFromAFreshPersistenceContext() {
        ChatMessage sent = chat.create(room.getId(), author.rawToken(), "새 조회 원문");
        Report accepted = reportService.create(room.getId(), reporter.rawToken(), sent.id(), "검토 요청");

        transactions.executeWithoutResult(status -> {
            Report loaded = reports.findById(accepted.getId()).orElseThrow();
            assertEquals("새 조회 원문", loaded.getEvidenceContent());
            assertEquals(sent.id(), loaded.getMessageEventId());
        });
    }

    @Test
    void reportAcceptedBeforeExpiryMayCommitAfterExpiry() {
        ChatMessage sent = chat.create(room.getId(), author.rawToken(), "경계 신고");
        clock.set(sent.expiresAt().minusNanos(1000));
        Integer id = transactions.execute(status -> {
            Report accepted = reportService.create(room.getId(), reporter.rawToken(), sent.id(), "경계 확인");
            clock.set(sent.expiresAt().plusSeconds(1));
            return accepted.getId();
        });

        assertTrue(reports.existsById(id));
        assertTrue(messages.findByIdAndExpiresAtGreaterThan(sent.id(), sent.expiresAt()).isEmpty());
    }
}
