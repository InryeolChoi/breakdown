package com.breakground.report;

import com.breakground.anonymoususer.*;
import com.breakground.chat.RecentMessageStore;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import com.breakground.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
    @Autowired RecentMessageStore messages;
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
    void savesOnlyServerVerifiedEvidenceAndKeepsItAfterBufferExpiry() throws Exception {
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
        assertTrue(messages.find(messageId).isEmpty());
        assertTrue(reports.findById(reportId).isPresent());
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
        assertEquals(now.withHour(11).withMinute(0), messages.find(messageId).orElseThrow().expiresAt());
        clock.set(now.withHour(11).withMinute(0));
        assertTrue(messages.find(messageId).isEmpty());
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
        UUID messageId = send("😀".repeat(140));
        assertEquals(140, messages.find(messageId).orElseThrow().content().codePointCount(0, 280));
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
        clock.set(now.plusDays(30).minusNanos(1000));
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
                messages.find(messageId).orElseThrow(), "반복신고", now, now.plusDays(30));
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
    }

    @AfterTransaction
    void rollbackAlsoRemovesMessagesFromTheBuffer() {
        for (UUID id : sentIds) {
            assertTrue(messages.find(id).isEmpty(), "롤백된 메시지가 메모리에 남아 있으면 안 된다.");
        }
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
