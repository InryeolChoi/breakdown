package com.breakground.report;

import com.breakground.anonymoususer.*;
import com.breakground.chat.ChatMessage;
import com.breakground.chat.Message;
import com.breakground.chat.MessageCleanup;
import com.breakground.chat.MessageRepository;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import com.breakground.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"chat.cleanup-interval-ms=3600000", "report.cleanup-interval-ms=3600000"})
@AutoConfigureMockMvc
@Transactional
class ChatReportIntegrationTest {
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock testClock() {
            return new MutableClock(LocalDateTime.of(2026, 10, 5, 10, 30));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired RoomRepository rooms;
    @Autowired AnonymousUserService users;
    @Autowired ReportRepository reports;
    @Autowired MessageRepository messages;
    @Autowired MessageCleanup messageCleanup;
    @Autowired EntityManager entityManager;
    @Autowired ReportCleanup cleanup;
    @Autowired MutableClock clock;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 5, 10, 30);
    private final List<UUID> sentIds = new ArrayList<>();
    private Room room;
    private AnonymousUserSession author;
    private AnonymousUserSession reporter;

    @BeforeEach
    void prepare() {
        clock.set(now);
        room = rooms.saveAndFlush(new Room("테스트방", LocalTime.of(10, 0), LocalTime.of(11, 0), true, true));
        author = users.getOrCreateAnonymousUser("작성자", null);
        reporter = users.getOrCreateAnonymousUser("신고자", null);
        author.user().recordVisit(now);
        reporter.user().recordVisit(now);
    }

    @Test
    void savesOnlyServerVerifiedEvidenceAndKeepsItAfterOriginalDeletion() throws Exception {
        UUID messageId = send("서버원본");
        String json = mvc.perform(post("/rooms/{id}/reports", room.getId())
                        .cookie(cookie(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + messageId + "\",\"reason\":\"욕설\","
                                + "\"evidenceContent\":\"조작내용\",\"reportedUserId\":999999}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer reportId = JsonPath.read(json, "$.id");
        Report saved = reports.findById(reportId).orElseThrow();
        assertEquals("서버원본", saved.getEvidenceContent());
        assertEquals(author.user().getId(), saved.getReportedUser().getId());
        assertEquals(reporter.user().getId(), saved.getReporter().getId());
        assertEquals(room.getId(), saved.getRoom().getId());
        assertEquals(now.plusDays(30), saved.getEvidenceExpiresAt());
        clock.set(now.plusMinutes(15));
        assertTrue(messages.findByIdAndExpiresAtGreaterThan(messageId, now.plusMinutes(15)).isEmpty());
        assertTrue(messages.findById(messageId).isPresent());
        clock.set(now.withHour(11).withMinute(10));
        messageCleanup.removeReadyForCleanup();
        entityManager.clear();
        assertTrue(messages.findById(messageId).isEmpty());
        assertTrue(reports.findById(reportId).isPresent());
        assertEquals("서버원본", reports.findById(reportId).orElseThrow().getEvidenceContent());
    }

    @Test
    void duplicateReportIsRejectedButAnotherReporterCanReport() throws Exception {
        UUID messageId = send("원본");
        report(messageId, reporter, 201);
        report(messageId, reporter, 409);
        AnonymousUserSession other = users.getOrCreateAnonymousUser("다른신고자", null);
        other.user().recordVisit(now);
        report(messageId, other, 201);
    }

    @Test
    void expiredAndUnknownEvidenceCannotBeReported() throws Exception {
        UUID messageId = send("원본");
        clock.set(now.plusMinutes(15));
        report(messageId, reporter, 410);
        report(UUID.randomUUID(), reporter, 410);
    }

    @Test
    void roomClosingEndsEvidenceLifetimeAndNextOpeningCannotReviveIt() throws Exception {
        clock.set(now.withHour(10).withMinute(59));
        UUID messageId = send("마감직전");
        assertEquals(now.withHour(11).withMinute(0), messages.findById(messageId).orElseThrow().getExpiresAt());
        clock.set(now.withHour(11).withMinute(0));
        assertTrue(messages.findByIdAndExpiresAtGreaterThan(messageId, now.withHour(11).withMinute(0)).isEmpty());
        report(messageId, reporter, 403);
        clock.set(now.plusDays(1));
        report(messageId, reporter, 410);
    }

    @Test
    void missingExpiredAndBannedUsersCannotSend() throws Exception {
        mvc.perform(post("/rooms/{id}/messages", room.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}"))
                .andExpect(status().isUnauthorized());
        author.user().recordVisit(now.minusDays(7));
        sendExpecting("안녕", 401);
        author.user().recordVisit(now);
        author.user().banForOneWeek(now);
        sendExpecting("안녕", 403);
    }

    @Test
    void contentIsValidatedAndWriterComesFromCookie() throws Exception {
        sendExpecting(" ", 400);
        sendExpecting("가".repeat(141), 400);
        mvc.perform(post("/rooms/{id}/messages", room.getId())
                        .cookie(cookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\\u0000\"}"))
                .andExpect(status().isBadRequest());
        UUID messageId = send("😀".repeat(140));
        assertEquals(140, messages.findById(messageId).orElseThrow().getContent().codePointCount(0, 280));
        report(messageId, reporter, 201);
        String body = mvc.perform(post("/rooms/{id}/messages", room.getId())
                        .cookie(cookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"안녕\",\"authorId\":999999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(author.user().getId()))
                .andExpect(jsonPath("$.rawToken").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertNotNull(JsonPath.read(body, "$.id"));
    }

    @Test
    void cannotReportOneselfOrUseEvidenceFromAnotherRoom() throws Exception {
        UUID messageId = send("원본");
        report(messageId, author, 400);
        Room other = rooms.saveAndFlush(new Room("다른방", LocalTime.of(10, 0), LocalTime.of(11, 0), true, true));
        mvc.perform(post("/rooms/{id}/reports", other.getId())
                        .cookie(cookie(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(messageId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesExpiredReportsAtThirtyDays() throws Exception {
        UUID messageId = send("원본");
        String response = mvc.perform(post("/rooms/{id}/reports", room.getId())
                        .cookie(cookie(reporter)).contentType(MediaType.APPLICATION_JSON).content(reportBody(messageId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer reportId = JsonPath.read(response, "$.id");
        // PostgreSQL timestamp의 정밀도는 마이크로초다.
        clock.set(now.plusDays(30).minusNanos(100));
        cleanup.removeExpired();
        assertTrue(reports.existsById(reportId));
        clock.set(now.plusDays(30));
        cleanup.removeExpired();
        assertFalse(reports.existsById(reportId));
    }

    @Test
    void databaseConstraintAlsoPreventsDuplicateReports() throws Exception {
        UUID messageId = send("원본");
        report(messageId, reporter, 201);
        Report duplicate = new Report(reporter.user(), author.user(), room,
                ChatMessage.from(messages.findById(messageId).orElseThrow()), "반복신고", now, now.plusDays(30));
        assertThrows(DataIntegrityViolationException.class, () -> reports.saveAndFlush(duplicate));
    }

    @Test
    void missingMessageIdAndBlankReasonAreRejected() throws Exception {
        mvc.perform(post("/rooms/{id}/reports", room.getId())
                        .cookie(cookie(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isBadRequest());
        UUID id = send("원본");
        mvc.perform(post("/rooms/{id}/reports", room.getId())
                        .cookie(cookie(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + id + "\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/rooms/{id}/reports", room.getId())
                        .cookie(cookie(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + id + "\",\"reason\":\"욕설\\u0000\"}"))
                .andExpect(status().isBadRequest());
    }

    @AfterTransaction
    void testRollbackAlsoRemovesMessagesFromPostgres() {
        for (UUID id : sentIds) {
            assertFalse(messages.existsById(id), "롤백된 메시지가 DB에 남아 있으면 안 된다.");
        }
    }

    @Test
    void cleanupWaitsUntilTenMinutesAfterRoomClosing() throws Exception {
        UUID messageId = send("만료되어도 삭제 기한까지 보관");
        clock.set(now.plusMinutes(15));
        messageCleanup.removeReadyForCleanup();
        entityManager.clear();
        assertTrue(messages.findById(messageId).isPresent());
        clock.set(now.withHour(11).withMinute(10).minusNanos(100));
        messageCleanup.removeReadyForCleanup();
        assertTrue(messages.existsById(messageId));
        clock.set(now.withHour(11).withMinute(10));
        messageCleanup.removeReadyForCleanup();
        entityManager.clear();
        assertFalse(messages.existsById(messageId));
    }

    @Test
    void overnightMessagesShareTheOriginalOperatingPeriodsDeletionTime() throws Exception {
        // 금요일 밤과 토요일 새벽 모두 금요일 운영 구간에 속한다.
        room = rooms.saveAndFlush(new Room("야근테스트방", LocalTime.of(21, 0),
                LocalTime.of(2, 0), true, false));
        LocalDateTime friday = LocalDateTime.of(2026, 10, 9, 23, 50);
        clock.set(friday);
        UUID beforeMidnight = send("금요일 밤");
        clock.set(friday.plusMinutes(20));
        UUID afterMidnight = send("토요일 새벽");
        LocalDateTime closing = LocalDateTime.of(2026, 10, 10, 2, 0);

        assertEquals(closing.plusMinutes(10), messages.findById(beforeMidnight).orElseThrow().getDeleteAfter());
        assertEquals(closing.plusMinutes(10), messages.findById(afterMidnight).orElseThrow().getDeleteAfter());
        clock.set(closing);
        sendExpecting("닫힌 방", 403);
        clock.set(closing.plusMinutes(10));
        messageCleanup.removeReadyForCleanup();
        entityManager.clear();
        assertFalse(messages.existsById(beforeMidnight));
        assertFalse(messages.existsById(afterMidnight));
    }

    @Test
    void subMicrosecondClockNearClosingDoesNotRoundSentTimeUpToExpiration() throws Exception {
        LocalDateTime closing = now.withHour(11).withMinute(0);
        clock.set(closing.minusNanos(100));
        UUID id = send("닫히기 직전");
        messages.flush();
        entityManager.clear();
        Message loaded = messages.findById(id).orElseThrow();
        assertEquals(closing.minusNanos(1000), loaded.getSentAt());
        assertEquals(closing, loaded.getExpiresAt());
        assertEquals(closing.plusMinutes(10), loaded.getDeleteAfter());
    }

    private UUID send(String text) throws Exception {
        String result = mvc.perform(post("/rooms/{id}/messages", room.getId())
                        .cookie(cookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + text + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(result, "$.id"));
        sentIds.add(id);
        return id;
    }
    private void sendExpecting(String text, int statusCode) throws Exception {
        mvc.perform(post("/rooms/{id}/messages", room.getId())
                        .cookie(cookie(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + text + "\"}"))
                .andExpect(status().is(statusCode));
    }
    private void report(UUID id, AnonymousUserSession session, int statusCode) throws Exception {
        mvc.perform(post("/rooms/{id}/reports", room.getId())
                        .cookie(cookie(session)).contentType(MediaType.APPLICATION_JSON).content(reportBody(id)))
                .andExpect(status().is(statusCode));
    }
    private String reportBody(UUID id) { return "{\"messageId\":\"" + id + "\",\"reason\":\"욕설\"}"; }
    private Cookie cookie(AnonymousUserSession session) {
        return new Cookie(AnonymousUserCookies.NAME, session.rawToken());
    }
}
