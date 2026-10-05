package com.breakground.report;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.anonymoususer.AnonymousUserService;
import com.breakground.chat.ChatMessage;
import com.breakground.chat.Message;
import com.breakground.chat.MessageRepository;
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
import java.time.temporal.ChronoUnit;

@Service
public class ReportService {
    private final ReportRepository reports;
    private final RoomRepository rooms;
    private final AnonymousUserService sessions;
    private final MessageRepository messages;
    private final ReportProperties properties;
    private final Clock clock;

    public ReportService(ReportRepository reports, RoomRepository rooms,
                         AnonymousUserService sessions, MessageRepository messages,
                         ReportProperties properties, Clock clock) {
        this.reports = reports;
        this.rooms = rooms;
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
        // 사용자/방 조회가 끝난 뒤 원본을 확인할 시점의 시각을 사용한다.
        LocalDateTime checkedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (!room.isOpen(checkedAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지금은 닫힌 방입니다.");
        }
        Message original = messages.findByIdAndExpiresAtGreaterThan(messageId, checkedAt).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.GONE, "신고할 메시지 원본이 만료됐거나 없습니다."));
        ChatMessage evidence = ChatMessage.from(original);
        if (!evidence.roomId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이 방의 메시지가 아닙니다.");
        }
        if (evidence.authorId().equals(reporter.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자신의 메시지는 신고할 수 없습니다.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유는 1~500자로 입력해주세요.");
        }
        if (reason.indexOf('\0') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유에 NUL 문자를 사용할 수 없습니다.");
        }
        if (reports.existsByReporter_IdAndMessageEventId(reporter.getId(), messageId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 메시지입니다.");
        }
        AnonymousUser target = original.getAuthor();
        Report report = new Report(reporter, target, room, evidence, reason.strip(), checkedAt,
                checkedAt.plus(properties.evidenceRetention()));
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
