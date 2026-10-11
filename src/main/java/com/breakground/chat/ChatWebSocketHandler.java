package com.breakground.chat;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.breakground.chat.dto.MessageCreateRequest;
import com.breakground.chat.dto.ChatErrorResponse;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final RoomSessionRegistry registry;
    private final ChatService chatService;
    private final ChatMessageCodec chatMessageCodec;

    public ChatWebSocketHandler(RoomSessionRegistry registry,
                                ChatService chatService,
                                ChatMessageCodec chatMessageCodec) {
        this.registry = registry;
        this.chatService = chatService;
        this.chatMessageCodec = chatMessageCodec;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
        throws Exception {
        Integer roomId = (Integer) session.getAttributes().get("roomId");
        registry.register(roomId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) throws Exception {
        Integer roomId = (Integer) session.getAttributes().get("roomId");
        registry.unregister(roomId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {
        // 방 ID를 읽는다.
        Integer roomId = (Integer) session.getAttributes().get("roomId");
        // 받은 메시지의 문자열을 꺼낸다.
        String payload = message.getPayload();
        // 사용자를 식별할 토큰을 읽는다.
        String rawToken = (String) session.getAttributes().get("rawToken");
        ChatMessage saved;
        try {
            // JSON 문자열을 요청 DTO로 해석하고, 검증한 뒤 DB에 저장한다.
            MessageCreateRequest request = chatMessageCodec.decode(payload);
            saved = chatService.create(roomId, rawToken, request.content());
        } catch (ResponseStatusException exception) {
            sendErrorAndApplyPolicy(roomId, session, exception);
            return;
        }
        // 저장 결과를 전송용 메시지로 만든다.
        TextMessage response = chatMessageCodec.encode(saved);
        registry.broadcast(roomId, response);
    }

    private void sendErrorAndApplyPolicy(Integer roomId, WebSocketSession session,
                                        ResponseStatusException exception)
            throws IOException {
        String reason = exception.getReason();
        ChatErrorResponse response = new ChatErrorResponse(
                "ERROR", exception.getStatusCode().value(),
                reason == null ? "메시지를 처리할 수 없습니다." : reason);
        TextMessage error = chatMessageCodec.encodeError(response);
        boolean terminate = exception.getStatusCode().value() != HttpStatus.BAD_REQUEST.value();
        // 방송과 같은 세션 객체를 잠가 오류 응답과 방송의 전송도 겹치지 않게 한다.
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(error);
                }
            } finally {
                // 안내 전송이 실패해도 유효하지 않은 연결은 방송 목록에서 제거한다.
                if (terminate) {
                    registry.unregister(roomId, session);
                    if (session.isOpen()) {
                        session.close(CloseStatus.POLICY_VIOLATION);
                    }
                }
            }
        }
    }
}
