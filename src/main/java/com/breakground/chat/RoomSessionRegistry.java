package com.breakground.chat;

import com.breakground.room.Room;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoomSessionRegistry {
    private final Map<Integer, Set<WebSocketSession>> roomSetMap =
        new ConcurrentHashMap<Integer, Set<WebSocketSession>>();

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

    public void broadcast(Integer roomId, TextMessage message) throws IOException {
        Set<WebSocketSession> sessions = roomSetMap.get(roomId);
        if (sessions == null)
            return;
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen())
                    s.sendMessage(message);
                else
                    unregister(roomId, s);
            } catch (IOException e) {
                unregister(roomId, s);
            }
        }
    }
}
