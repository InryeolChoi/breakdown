package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.anonymoususer.AnonymousUserService;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class ChatService {
    private final RoomRepository rooms;
    private final AnonymousUserService users;
    private final MessageRepository messages;
    private final ChatProperties properties;
    private final Clock clock;

    public ChatService(RoomRepository rooms, AnonymousUserService users, MessageRepository messages,
                       ChatProperties properties, Clock clock) {
        this.rooms = rooms;
        this.users = users;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ChatMessage create(Integer roomId, String rawToken, String content) {
        // PostgreSQL timestamp 정밀도에 맞춰 방 종료 직전 반올림을 피한다.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        AnonymousUser author = users.requireActiveUser(rawToken, now);
        Room room = rooms.findById(roomId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        if (!room.isOpen(now)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지금은 닫힌 방입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지를 입력해주세요.");
        }
        String text = content.strip();
        if (text.indexOf('\0') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지에 NUL 문자를 사용할 수 없습니다.");
        }
        if (text.codePointCount(0, text.length()) > 140) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 140자 이하여야 합니다.");
        }
        LocalDateTime expiresAt = now.plus(properties.reportableDuration());
        LocalDateTime roomClosesAt = room.currentClosingTime(now);
        if (roomClosesAt.isBefore(expiresAt)) {
            expiresAt = roomClosesAt;
        }
        LocalDateTime deleteAfter = roomClosesAt.plus(properties.deletionDelay());
        Message message = messages.save(new Message(room, author, text, now, expiresAt, deleteAfter));
        return ChatMessage.from(message);
    }
}
