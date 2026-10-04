package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.anonymoususer.AnonymousUserService;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChatService {
    private final RoomRepository rooms;
    private final AnonymousUserService users;
    private final RecentMessageStore messages;
    private final ChatProperties properties;
    private final Clock clock;

    public ChatService(RoomRepository rooms, AnonymousUserService users, RecentMessageStore messages,
                       ChatProperties properties, Clock clock) {
        this.rooms = rooms;
        this.users = users;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ChatMessage create(Integer roomId, String rawToken, String content) {
        LocalDateTime now = LocalDateTime.now(clock);
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
        if (text.codePointCount(0, text.length()) > 140) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 140자 이하여야 합니다.");
        }
        LocalDateTime expiresAt = now.plus(properties.messageRetention());
        LocalDateTime roomClosesAt = room.currentClosingTime(now);
        if (roomClosesAt.isBefore(expiresAt)) {
            expiresAt = roomClosesAt;
        }
        ChatMessage message = new ChatMessage(UUID.randomUUID(), roomId, author.getId(),
                author.getNickname(), text, now, expiresAt);
        messages.add(message);
        // PostgreSQL 트랜잭션은 Map을 롤백하지 않으므로 실패 시 직접 제거한다.
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        messages.remove(message.id());
                    }
                }
            }
        );
        return message;
    }
}
