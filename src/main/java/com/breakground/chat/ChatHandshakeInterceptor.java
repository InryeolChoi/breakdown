package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUserCookies;
import com.breakground.anonymoususer.AnonymousUserService;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    private static final UriTemplate ROOM_PATH = new UriTemplate("/ws/rooms/{roomId}");
    private final AnonymousUserService users;
    private final RoomRepository rooms;
    private final Clock clock;

    public ChatHandshakeInterceptor(AnonymousUserService users, RoomRepository rooms, Clock clock) {
        this.users = users;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            String rawToken = readToken(request);
            if (rawToken == null || rawToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "익명 사용자를 먼저 준비해주세요.");
            }
            Integer roomId = Integer.valueOf(ROOM_PATH.match(request.getURI().getPath()).get("roomId"));
            LocalDateTime now = LocalDateTime.now(clock);
            Room room = rooms.findById(roomId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
            if (!room.isOpen(now)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 운영 중인 방이 아닙니다.");
            }
            // 방 검사를 먼저 끝내어 거절된 방 접속이 방문 시각을 갱신하지 않게 한다.
            users.requireActiveUser(rawToken, now);
            attributes.put("roomId", roomId);
            attributes.put("rawToken", rawToken);
            return true;
        } catch (ResponseStatusException error) {
            response.setStatusCode(error.getStatusCode());
            return false;
        } catch (NumberFormatException error) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    private String readToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (AnonymousUserCookies.NAME.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
