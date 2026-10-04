package com.breakground.report;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.anonymoususer.AnonymousUserRepository;
import com.breakground.anonymoususer.AnonymousUserService;
import com.breakground.chat.ChatMessage;
import com.breakground.chat.RecentMessageStore;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ReportService {
    private final ReportRepository reports;
    private final RoomRepository rooms;
    private final AnonymousUserRepository users;
    private final AnonymousUserService sessions;
    private final RecentMessageStore messages;
    private final ReportProperties properties;
    private final Clock clock;

    public ReportService(ReportRepository reports, RoomRepository rooms, AnonymousUserRepository users,
                         AnonymousUserService sessions, RecentMessageStore messages,
                         ReportProperties properties, Clock clock) {
        this.reports = reports;
        this.rooms = rooms;
        this.users = users;
        this.sessions = sessions;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Report create(Integer roomId, String rawToken, UUID messageId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        AnonymousUser reporter = sessions.requireActiveUser(rawToken, now);
        Room room = rooms.findById(roomId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        if (!room.isOpen(now)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지금은 닫힌 방입니다.");
        }
        ChatMessage evidence = messages.find(messageId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.GONE, "신고할 메시지 원본이 만료됐거나 없습니다."));
        if (!evidence.roomId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이 방의 메시지가 아닙니다.");
        }
        if (evidence.authorId().equals(reporter.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자신의 메시지는 신고할 수 없습니다.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유는 1~500자로 입력해주세요.");
        }
        if (reports.existsByReporter_IdAndMessageEventId(reporter.getId(), messageId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 메시지입니다.");
        }
        AnonymousUser target = users.findById(evidence.authorId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.GONE, "메시지 작성자를 찾을 수 없습니다."));
        Report report = new Report(reporter, target, room, evidence, reason.strip(), now,
                now.plus(properties.evidenceRetention()));
        try {
            return reports.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 중복 신고도 DB UNIQUE 제약으로 막는다.
            String detail = e.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("uq_report_reporter_message")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 메시지입니다.");
            }
            throw e;
        }
    }
}
