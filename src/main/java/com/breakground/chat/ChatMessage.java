package com.breakground.chat;

import java.time.LocalDateTime;
import java.util.UUID;

// HTTP 응답과 신고 증거 복사에 사용하는 불변 스냅샷. DB Entity와 구분한다.
public record ChatMessage(UUID id, Integer roomId, Integer authorId, String nickname,
                          String content, LocalDateTime sentAt, LocalDateTime expiresAt) {
    public static ChatMessage from(Message message) {
        return new ChatMessage(message.getId(), message.getRoom().getId(), message.getAuthor().getId(),
                message.getNickname(), message.getContent(), message.getSentAt(), message.getExpiresAt());
    }
}
