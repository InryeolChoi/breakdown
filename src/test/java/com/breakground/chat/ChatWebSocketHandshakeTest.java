package com.breakground.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.breakground.anonymoususer.AnonymousUserRepository;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import com.breakground.support.MutableClock;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"chat.cleanup-interval-ms=3600000", "report.cleanup-interval-ms=3600000"})
class ChatWebSocketHandshakeTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 30);

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        MutableClock handshakeTestClock() {
            return new MutableClock(NOW);
        }
    }

    @LocalServerPort
    private int port;
    @Autowired private RoomRepository rooms;
    @Autowired private AnonymousUserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private JsonMapper mapper;
    private final List<Integer> roomIds = new ArrayList<>();
    private final List<Integer> userIds = new ArrayList<>();

    @AfterEach
    void removeOnlyThisTestsFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Integer id : roomIds) {
                entityManager.createQuery("delete from Message m where m.room.id = :roomId")
                        .setParameter("roomId", id).executeUpdate();
            }
            rooms.deleteAllById(roomIds);
            users.deleteAllById(userIds);
        });
    }

    @Test
    void connectionWithoutAnonymousCookieIsRejected() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var connection = client.newWebSocketBuilder().buildAsync(
                    URI.create("ws://localhost:" + port + "/ws/rooms/1"),
                    new WebSocket.Listener() {});
            // 예상과 달리 연결돼도 테스트가 소켓을 남기지 않도록 정리한다.
            connection.thenAccept(WebSocket::abort);

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> connection.get(3, TimeUnit.SECONDS));
            WebSocketHandshakeException handshakeFailure = assertInstanceOf(
                    WebSocketHandshakeException.class, failure.getCause());
            assertEquals(401, handshakeFailure.getResponse().statusCode());
        }
    }

    @Test
    void preparedUserCanConnectToOpenRoomAndReceiveTheirMessage() throws Exception {
        Room room = rooms.saveAndFlush(new Room("소켓테스트방", LocalTime.of(10, 0),
                LocalTime.of(11, 0), true, true));
        roomIds.add(room.getId());
        try (HttpClient client = HttpClient.newHttpClient()) {
            String cookie = prepareUserCookie(client);
            CompletableFuture<String> received = new CompletableFuture<>();
            StringBuilder buffer = new StringBuilder();
            var connection = client.newWebSocketBuilder().header("Cookie", cookie).buildAsync(
                    URI.create("ws://localhost:" + port + "/ws/rooms/" + room.getId()),
                    new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket socket) {
                            socket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                            buffer.append(data);
                            if (last) {
                                received.complete(buffer.toString());
                            }
                            socket.request(1);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket socket, Throwable error) {
                            received.completeExceptionally(error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                            received.completeExceptionally(new IllegalStateException("응답 전에 연결 종료: " + code));
                            return null;
                        }
                    });
            try {
                WebSocket socket = connection.get(3, TimeUnit.SECONDS);
                socket.sendText("{\"content\":\"안녕\"}", true).get(3, TimeUnit.SECONDS);
                var response = mapper.readTree(received.get(3, TimeUnit.SECONDS));
                assertEquals("안녕", response.get("content").stringValue());
                assertEquals(room.getId().intValue(), response.get("roomId").intValue());
                assertEquals(userIds.getFirst().intValue(), response.get("authorId").intValue());
            } finally {
                connection.thenAccept(WebSocket::abort);
            }
        }
    }

    private String prepareUserCookie(HttpClient client) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/anonymoususer"))
                .timeout(java.time.Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"소켓사용자\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        userIds.add(mapper.readTree(response.body()).get("id").intValue());
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    @Test
    void preparedUserCannotConnectAtRoomClosingTime() throws Exception {
        Room room = rooms.saveAndFlush(new Room("닫힌소켓방", LocalTime.of(9, 0),
                LocalTime.of(10, 30), true, true));
        roomIds.add(room.getId());
        try (HttpClient client = HttpClient.newHttpClient()) {
            String cookie = prepareUserCookie(client);
            var connection = client.newWebSocketBuilder().header("Cookie", cookie).buildAsync(
                    URI.create("ws://localhost:" + port + "/ws/rooms/" + room.getId()),
                    new WebSocket.Listener() {});
            connection.thenAccept(WebSocket::abort);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> connection.get(3, TimeUnit.SECONDS));
            WebSocketHandshakeException handshakeFailure = assertInstanceOf(
                    WebSocketHandshakeException.class, failure.getCause());
            assertEquals(403, handshakeFailure.getResponse().statusCode());
        }
    }

    @ParameterizedTest
    @CsvSource({"unknown, 401", "expired, 401", "banned, 403"})
    void invalidUserCannotConnect(String state, int expectedStatus) throws Exception {
        Room room = rooms.saveAndFlush(new Room("사용자검사방", LocalTime.of(10, 0),
                LocalTime.of(11, 0), true, true));
        roomIds.add(room.getId());
        try (HttpClient client = HttpClient.newHttpClient()) {
            String cookie = "anonymous_user_token=unknown-token";
            if (!state.equals("unknown")) {
                cookie = prepareUserCookie(client);
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    var user = users.findById(userIds.getFirst()).orElseThrow();
                    if (state.equals("expired")) {
                        user.recordVisit(NOW.minusDays(7));
                    } else {
                        user.banForOneWeek(NOW);
                    }
                });
            }
            assertConnectionRejected(client, "/ws/rooms/" + room.getId(), cookie, expectedStatus);
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 404", "abc, 400"})
    void unknownOrMalformedRoomIsRejected(String roomId, int expectedStatus) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertConnectionRejected(client, "/ws/rooms/" + roomId,
                    prepareUserCookie(client), expectedStatus);
        }
    }

    private void assertConnectionRejected(HttpClient client, String path, String cookie, int status) {
        var connection = client.newWebSocketBuilder().header("Cookie", cookie).buildAsync(
                URI.create("ws://localhost:" + port + path), new WebSocket.Listener() {});
        connection.thenAccept(WebSocket::abort);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> connection.get(3, TimeUnit.SECONDS));
        WebSocketHandshakeException handshakeFailure = assertInstanceOf(
                WebSocketHandshakeException.class, failure.getCause());
        assertEquals(status, handshakeFailure.getResponse().statusCode());
    }
}
