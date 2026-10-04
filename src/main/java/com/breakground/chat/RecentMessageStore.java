package com.breakground.chat;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RecentMessageStore {
    private final Map<UUID, ChatMessage> messages = new HashMap<>();
    private final ChatProperties properties;
    private final Clock clock;

    public RecentMessageStore(ChatProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    // 조회/만료 정리/용량 확인/추가를 하나의 잠금 안에서 처리한다.
    public synchronized void add(ChatMessage message) {
        removeExpired();
        if (messages.size() >= properties.maxBufferedMessages()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 메시지를 보내주세요.");
        }
        messages.put(message.id(), message);
    }

    public synchronized Optional<ChatMessage> find(UUID messageId) {
        ChatMessage message = messages.get(messageId);
        if (message == null) {
            return Optional.empty();
        }
        if (!LocalDateTime.now(clock).isBefore(message.expiresAt())) {
            messages.remove(messageId);
            return Optional.empty();
        }
        return Optional.of(message);
    }

    public synchronized void remove(UUID messageId) {
        messages.remove(messageId);
    }

    @Scheduled(fixedDelayString = "${chat.cleanup-interval-ms:1000}")
    public synchronized void removeExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        messages.values().removeIf(message -> !now.isBefore(message.expiresAt()));
    }
}
