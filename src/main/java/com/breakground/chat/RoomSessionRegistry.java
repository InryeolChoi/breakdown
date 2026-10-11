package com.breakground.chat;

import com.breakground.room.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;

@Component
public class RoomSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(RoomSessionRegistry.class);
    private final Map<Integer, Set<WebSocketSession>> roomSetMap = new ConcurrentHashMap<Integer, Set<WebSocketSession>>();

    public void register(Integer roomId, WebSocketSession newSession) {
        Set<WebSocketSession> sessions = roomSetMap.computeIfAbsent(roomId,
            id -> ConcurrentHashMap.newKeySet());
        if (!sessions.contains(newSession)) {
            sessions.add(newSession);
        }
    }

    public void unregister(Integer roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSetMap.get(roomId);
        if (sessions == null)
            return;
        sessions.remove(session);
    }

    public void closeExpiredSessions(LocalDateTime now,
                                     TextMessage notice) {
        for (var entry : roomSetMap.entrySet()) {
            Integer roomId = entry.getKey();
            Set<WebSocketSession> sessions = entry.getValue();

            for (WebSocketSession s : sessions) {
                LocalDateTime closesAt = (LocalDateTime) s.getAttributes().get("closesAt");
                if (!now.isBefore(closesAt)) {
                    try {
                        synchronized (s) {
                            try {
                                if (s.isOpen())
                                    s.sendMessage(notice);
                            } finally {
                                unregister(roomId, s);
                                if (s.isOpen()) {
                                    s.close(CloseStatus.POLICY_VIOLATION);
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.warn("WebSocket 종료 처리 실패: roomId={}, sessionId={}",
                                roomId, s.getId(), e);
                    }
                }
            }
        }
    }

    public void broadcast(Integer roomId, TextMessage message) throws IOException {
        Set<WebSocketSession> sessions = roomSetMap.get(roomId);
        if (sessions == null)
            return;
        for (WebSocketSession s : sessions) {
            try {
                synchronized (s) {
                    if (s.isOpen())
                        s.sendMessage(message);
                    else
                        unregister(roomId, s);
                }
            } catch (IOException e) {
                unregister(roomId, s);
            }
        }
    }
}
