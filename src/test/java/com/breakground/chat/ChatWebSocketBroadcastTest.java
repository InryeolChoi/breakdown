package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUserRepository;
import com.breakground.room.Room;
import com.breakground.room.RoomRepository;
import com.breakground.support.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"chat.cleanup-interval-ms=3600000", "report.cleanup-interval-ms=3600000"})
class ChatWebSocketBroadcastTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 30);

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        MutableClock broadcastTestClock() {
            return new MutableClock(NOW);
        }
    }

    @LocalServerPort private int port;
    @Autowired private RoomRepository rooms;
    @Autowired private AnonymousUserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private JsonMapper mapper;
    @Autowired private MutableClock clock;

    private HttpClient client;
    private final List<Integer> roomIds = new ArrayList<>();
    private final List<Integer> userIds = new ArrayList<>();
    private final List<ChatClient> connections = new ArrayList<>();

    @BeforeEach
    void prepareClient() {
        clock.set(NOW);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterEach
    void removeOnlyThisTestsConnectionsAndFixtures() {
        connections.forEach(ChatClient::close);
        if (client != null) {
            client.close();
        }
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
    void senderAndAnotherUserInTheSameRoomReceiveTheSameServerMessage() throws Exception {
        Integer roomId = createOpenRoom();
        PreparedUser alice = prepareUser("Alice");
        PreparedUser bob = prepareUser("Bob");
        ChatClient aliceSocket = connect(roomId, alice);
        ChatClient bobSocket = connect(roomId, bob);

        aliceSocket.send("{\"content\":\"같은 방에 안녕\"}");

        JsonNode senderResponse = aliceSocket.receive();
        JsonNode otherResponse = bobSocket.receive();
        assertEquals("같은 방에 안녕", otherResponse.get("content").stringValue());
        assertEquals(roomId.intValue(), otherResponse.get("roomId").intValue());
        assertEquals(alice.id(), otherResponse.get("authorId").intValue());
        assertEquals("Alice", otherResponse.get("nickname").stringValue());
        // 작성자도 별개의 메시지를 받는 것이 아니라 동일한 서버 메시지를 받는다.
        assertEquals(UUID.fromString(senderResponse.get("id").stringValue()),
                UUID.fromString(otherResponse.get("id").stringValue()));
        assertEquals(senderResponse, otherResponse);
    }

    @Test
    void messagesStayInTheirOwnRoomInBothDirections() throws Exception {
        Integer roomA = createOpenRoom();
        Integer roomB = createOpenRoom();
        ChatClient alice = connect(roomA, prepareUser("Alice"));
        ChatClient bob = connect(roomA, prepareUser("Bob"));
        ChatClient carol = connect(roomB, prepareUser("Carol"));

        alice.send("{\"content\":\"A방 메시지\"}");
        assertEquals("A방 메시지", alice.receive().get("content").stringValue());
        assertEquals("A방 메시지", bob.receive().get("content").stringValue());

        // B방도 정상 수신 중임을 확인한다. A방 메시지가 섞이면 이 검증부터 실패한다.
        carol.send("{\"content\":\"B방 메시지\"}");
        JsonNode response = carol.receive();
        assertEquals("B방 메시지", response.get("content").stringValue());
        assertEquals(roomB.intValue(), response.get("roomId").intValue());

        alice.assertNoFurtherEvents();
        bob.assertNoFurtherEvents();
        carol.assertNoFurtherEvents();
    }

    @Test
    void userCanReconnectAndReceiveNewMessagesWhileTheOtherUserStaysConnected() throws Exception {
        Integer roomId = createOpenRoom();
        PreparedUser aliceUser = prepareUser("Alice");
        PreparedUser bobUser = prepareUser("Bob");
        ChatClient alice = connect(roomId, aliceUser);
        ChatClient bob = connect(roomId, bobUser);

        alice.send("{\"content\":\"재접속 전\"}");
        alice.receive();
        bob.receive();
        bob.closeGracefully();

        ChatClient reconnectedBob = connect(roomId, bobUser);
        alice.send("{\"content\":\"재접속 후\"}");
        JsonNode aliceResponse = alice.receive();
        JsonNode bobResponse = reconnectedBob.receive();
        assertEquals("재접속 후", bobResponse.get("content").stringValue());
        assertEquals(aliceResponse, bobResponse);
    }

    static Stream<String> invalidMessages() {
        return Stream.of("not json", "{\"content\":\"   \"}",
                "{\"content\":\"" + "가".repeat(141) + "\"}");
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    void inputErrorOnlyReachesSenderAndTheyCanSendAgain(String payload) throws Exception {
        Integer roomId = createOpenRoom();
        ChatClient alice = connect(roomId, prepareUser("Alice"));
        ChatClient bob = connect(roomId, prepareUser("Bob"));

        alice.send(payload);

        JsonNode error = alice.receive();
        assertEquals("ERROR", error.get("type").stringValue());
        assertEquals(400, error.get("status").intValue());
        assertFalse(error.get("message").stringValue().isBlank());
        bob.assertNoFurtherEvents();

        // 오류 이후에도 같은 연결에서 재입력하고, 정상 메시지는 두 사용자 모두 받는다.
        alice.send("{\"content\":\"다시 보냅니다\"}");
        JsonNode senderResponse = alice.receive();
        assertEquals("다시 보냅니다", senderResponse.get("content").stringValue());
        assertEquals(senderResponse, bob.receive());
    }

    static Stream<Arguments> terminalErrors() {
        return Stream.of(
                Arguments.of("expired", 401, "익명 사용자 식별이 만료됐습니다."),
                Arguments.of("banned", 403, "현재 차단된 사용자입니다."),
                Arguments.of("roomClosed", 403, "지금은 닫힌 방입니다."));
    }

    @ParameterizedTest
    @MethodSource("terminalErrors")
    void terminalErrorReachesSenderBeforeTheirConnectionCloses(
            String scenario, int expectedStatus, String expectedMessage) throws Exception {
        Integer roomId = createOpenRoom();
        PreparedUser aliceUser = prepareUser("Alice");
        ChatClient alice = connect(roomId, aliceUser);
        ChatClient bob = connect(roomId, prepareUser("Bob"));
        // 접속 검증은 통과했지만 연결 이후 정책이 달라진 경우를 재현한다.
        switch (scenario) {
            case "expired" -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    users.findById(aliceUser.id()).orElseThrow().recordVisit(NOW.minusDays(7)));
            case "banned" -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    users.findById(aliceUser.id()).orElseThrow().banForOneWeek(NOW));
            case "roomClosed" -> clock.set(NOW.withHour(11).withMinute(0));
            default -> throw new IllegalArgumentException(scenario);
        }

        alice.send("{\"content\":\"정책 변경 후 전송\"}");

        JsonNode error = alice.receive();
        assertEquals("ERROR", error.get("type").stringValue());
        assertEquals(expectedStatus, error.get("status").intValue());
        assertEquals(expectedMessage, error.get("message").stringValue());
        assertEquals(1008, alice.awaitCloseStatus());
        bob.assertNoFurtherEvents();
    }

    private Integer createOpenRoom() {
        Room room = rooms.saveAndFlush(new Room("소켓방송방", LocalTime.of(10, 0),
                LocalTime.of(11, 0), true, true));
        roomIds.add(room.getId());
        return room.getId();
    }

    private PreparedUser prepareUser(String nickname) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/anonymoususer"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(java.util.Map.of("nickname", nickname))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        int id = mapper.readTree(response.body()).get("id").intValue();
        userIds.add(id);
        // HTTP API는 시스템 시간을 사용하므로 고정된 채팅 테스트 시각에 맞춰 준비한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                users.findById(id).orElseThrow().recordVisit(NOW.minusHours(1)));
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        return new PreparedUser(id, cookie);
    }

    private ChatClient connect(Integer roomId, PreparedUser user) throws Exception {
        ChatClient chatClient = new ChatClient(roomId, user.cookie());
        connections.add(chatClient);
        // 접속 Future 완료 직후의 서버 초기화와 메시지 전송이 경합하지 않도록 왕복을 확인한다.
        chatClient.awaitReady();
        return chatClient;
    }

    private record PreparedUser(int id, String cookie) {}

    private class ChatClient implements WebSocket.Listener, AutoCloseable {
        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private final CompletableFuture<Void> pong = new CompletableFuture<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final WebSocket socket;

        private ChatClient(Integer roomId, String cookie) throws Exception {
            var connection = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3))
                    .header("Cookie", cookie)
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws/rooms/" + roomId), this);
            try {
                socket = connection.get(3, TimeUnit.SECONDS);
            } catch (Exception error) {
                connection.thenAccept(WebSocket::abort);
                throw error;
            }
        }

        private void awaitReady() throws Exception {
            socket.sendPing(ByteBuffer.wrap(new byte[]{1})).get(3, TimeUnit.SECONDS);
            pong.get(3, TimeUnit.SECONDS);
        }

        private void send(String json) throws Exception {
            socket.sendText(json, true).get(3, TimeUnit.SECONDS);
        }

        private JsonNode receive() throws Exception {
            Object event = events.poll(3, TimeUnit.SECONDS);
            if (event == null) {
                throw new TimeoutException("3초 안에 채팅 응답을 받지 못했습니다.");
            }
            if (event instanceof Throwable error) {
                throw new AssertionError("채팅 응답 대신 연결 오류가 발생했습니다.", error);
            }
            return mapper.readTree((String) event);
        }

        private void assertNoFurtherEvents() throws InterruptedException {
            // 수신한 정상 응답 뒤 500ms 동안 추가 메시지뿐 아니라 연결 오류도 없어야 한다.
            assertNull(events.poll(500, TimeUnit.MILLISECONDS),
                    "다른 방의 메시지 또는 예상하지 않은 연결 오류를 받았습니다.");
        }

        private void closeGracefully() throws Exception {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "테스트 종료").get(3, TimeUnit.SECONDS);
            closed.get(3, TimeUnit.SECONDS);
        }

        private int awaitCloseStatus() throws Exception {
            return closed.get(3, TimeUnit.SECONDS);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                events.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            pong.complete(null);
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            events.add(error);
            pong.completeExceptionally(error);
            closed.completeExceptionally(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            var error = new IllegalStateException("채팅 연결 종료: " + statusCode);
            events.add(error);
            pong.completeExceptionally(error);
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void close() {
            socket.abort();
        }
    }
}
