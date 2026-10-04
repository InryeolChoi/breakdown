package com.breakground.chat.dto;

import com.breakground.chat.ChatMessage;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(UUID id, Integer roomId, Integer authorId, String nickname,
                                  String content, LocalDateTime sentAt, LocalDateTime expiresAt) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.id(), message.roomId(), message.authorId(),
                message.nickname(), message.content(), message.sentAt(), message.expiresAt());
    }
}
