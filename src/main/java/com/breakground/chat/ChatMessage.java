package com.breakground.chat;

import java.time.LocalDateTime;
import java.util.UUID;

// 일반 메시지는 DB Entity가 아니라 실행 중인 서버의 메모리에만 존재한다.
public record ChatMessage(UUID id, Integer roomId, Integer authorId, String nickname,
                          String content, LocalDateTime sentAt, LocalDateTime expiresAt) {
}
