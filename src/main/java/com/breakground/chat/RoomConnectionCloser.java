package com.breakground.chat;

import com.breakground.chat.dto.ChatErrorResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class RoomConnectionCloser {
    private final RoomSessionRegistry roomSessionRegistry;
    private final ChatMessageCodec chatMessageCodec;
    private final Clock clock;

    public RoomConnectionCloser(RoomSessionRegistry registry,
                                ChatMessageCodec chatMessageCodec,
                                Clock clock)
    {
        this.roomSessionRegistry = registry;
        this.chatMessageCodec = chatMessageCodec;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000)
    public void closeExpiredConnections() {
        LocalDateTime now = LocalDateTime.now(clock);
        TextMessage notice = chatMessageCodec.encodeError(
                new ChatErrorResponse("ERROR", 403, "지금은 닫힌 방입니다."));
        roomSessionRegistry.closeExpiredSessions(now, notice);
    }
}
