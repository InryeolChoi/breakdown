package com.breakground.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketRateLimitTest {
    private final ChatService chatService = mock(ChatService.class);
    private final RoomSessionRegistry registry = mock(RoomSessionRegistry.class);
    private final ChatWebSocketHandler handler =
            new ChatWebSocketHandler(registry, chatService, new ChatMessageCodec(JsonMapper.builder().build()));

    @Test
    void tooManyMessagesSendsAnErrorButKeepsTheConnection() throws Exception {
        WebSocketSession session = openSession();
        when(chatService.create(anyInt(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "천천히"));

        handler.handleTextMessage(session, new TextMessage("{\"content\":\"안녕\"}"));

        verify(session).sendMessage(any(TextMessage.class));
        verify(session, never()).close(any(CloseStatus.class));
        verify(registry, never()).unregister(anyInt(), any());
    }

    @Test
    void bannedUserStillGetsDisconnected() throws Exception {
        WebSocketSession session = openSession();
        when(chatService.create(anyInt(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "차단"));

        handler.handleTextMessage(session, new TextMessage("{\"content\":\"안녕\"}"));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>(Map.of("roomId", 1, "rawToken", "token"));
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
