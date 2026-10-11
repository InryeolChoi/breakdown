package com.breakground.chat;

import com.breakground.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RoomSessionRegistryTest {
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2026, 10, 12, 11, 30);
    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 12, 10, 30));

    @Test
    void notifiesThenClosesConnectionExactlyAtItsOperatingPeriodEnd() throws IOException {
        RoomSessionRegistry registry = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        LocalDateTime closesAt = LocalDateTime.of(2026, 10, 12, 11, 30);
        when(alice.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", closesAt));
        registry.register(1, alice);
        TextMessage notice = new TextMessage(
                "{\"type\":\"ERROR\",\"status\":403,\"message\":\"지금은 닫힌 방입니다.\"}");

        registry.closeExpiredSessions(closesAt, notice);

        var delivery = inOrder(alice);
        delivery.verify(alice).sendMessage(notice);
        delivery.verify(alice).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void closesFailedNoticeConnectionAndContinuesClosingOtherConnections() throws IOException {
        RoomSessionRegistry registry = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);
        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        when(bob.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        registry.register(1, alice);
        registry.register(1, bob);
        TextMessage notice = new TextMessage(
                "{\"type\":\"ERROR\",\"status\":403,\"message\":\"지금은 닫힌 방입니다.\"}");
        doThrow(new IOException("종료 안내 전송 실패"))
                .when(alice).sendMessage(notice);

        registry.closeExpiredSessions(CLOSES_AT, notice);

        // 실패한 전송도 실제로 시도했고, finally에서 해당 연결을 닫아야 한다.
        verify(alice).sendMessage(notice);
        verify(alice).close(CloseStatus.POLICY_VIOLATION);
        var bobDelivery = inOrder(bob);
        bobDelivery.verify(bob).sendMessage(notice);
        bobDelivery.verify(bob).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void broadcastsToBothConnectionsRegisteredInTheSameRoom() throws IOException {
        RoomSessionRegistry registry = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);
        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        when(bob.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));

        registry.register(1, alice);
        registry.register(1, bob);

        registry.broadcast(1, new TextMessage("점심 뭐 먹지?"));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(alice).sendMessage(sent.capture());
        verify(bob).sendMessage(sent.capture());
        List<String> receivedContents = sent.getAllValues().stream()
                .map(TextMessage::getPayload)
                .toList();
        assertEquals(List.of("점심 뭐 먹지?", "점심 뭐 먹지?"), receivedContents);
    }

    @Test
    void doesNotBroadcastToUnregisteredConnection() throws IOException {
        RoomSessionRegistry A = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);

        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        when(bob.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));

        A.register(1, alice);
        A.register(1, bob);

        A.unregister(1, alice);
        A.broadcast(1, new TextMessage("점심 뭐 먹지?"));

        verify(alice, never()).sendMessage(any(TextMessage.class));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(bob).sendMessage(sent.capture());
        assertEquals("점심 뭐 먹지?", sent.getValue().getPayload());
    }

    @Test
    void doesNotBroadcastToConnectionsInAnotherRoom() throws IOException {
        RoomSessionRegistry A = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);

        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        when(bob.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));

        A.register(1, alice);
        A.register(2, bob);

        A.broadcast(1, new TextMessage("저녁 뭐 먹지?"));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(alice).sendMessage(sent.capture());
        assertEquals("저녁 뭐 먹지?", sent.getValue().getPayload());

        verify(bob, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void removesFailedConnectionAndContinuesBroadcasting() throws IOException {
        RoomSessionRegistry A = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);

        A.register(1, alice);
        A.register(1, bob);

        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        when(bob.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));

        doThrow(new IOException("전송 실패"))
            .when(alice).sendMessage(any(TextMessage.class));
        A.broadcast(1, new TextMessage("첫 메시지"));
        A.broadcast(1, new TextMessage("두번째 메시지"));

        verify(alice, times(1)).sendMessage(any(TextMessage.class));;
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(bob, times(2)).sendMessage(sent.capture());

        List<String> sList = sent.getAllValues().stream()
            .map(TextMessage::getPayload)
            .toList();
        assertEquals(List.of("첫 메시지", "두번째 메시지"), sList);
    }

    @Test
    void concurrentBroadcastsDeliverBothMessagesWithoutOverlappingSends() throws Exception {
        RoomSessionRegistry registry = new RoomSessionRegistry(clock);
        WebSocketSession recipient = mock(WebSocketSession.class);
        when(recipient.isOpen()).thenReturn(true);
        when(recipient.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));

        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSend = new CountDownLatch(1);
        CountDownLatch secondBroadcastStarted = new CountDownLatch(1);
        AtomicBoolean sending = new AtomicBoolean();
        var received = new ConcurrentLinkedQueue<String>();

        // 외부 전송 경계: 느린 연결이며, 실제 컨테이너처럼 동시 전송을 허용하지 않는다.
        doAnswer(invocation -> {
            if (!sending.compareAndSet(false, true)) {
                throw new IllegalStateException("동일한 연결에 전송이 겹쳤습니다.");
            }
            try {
                TextMessage message = invocation.getArgument(0);
                if (message.getPayload().equals("첫 방송")) {
                    firstSendStarted.countDown();
                    assertTrue(releaseFirstSend.await(5, TimeUnit.SECONDS), "첫 전송 해제 시간 초과");
                }
                received.add(message.getPayload());
            } finally {
                sending.set(false);
            }
            return null;
        }).when(recipient).sendMessage(any(TextMessage.class));
        registry.register(1, recipient);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                registry.broadcast(1, new TextMessage("첫 방송"));
                return null;
            });
            try {
                assertTrue(firstSendStarted.await(3, TimeUnit.SECONDS), "첫 전송이 시작되지 않았습니다.");
                var second = executor.submit(() -> {
                    secondBroadcastStarted.countDown();
                    registry.broadcast(1, new TextMessage("두 번째 방송"));
                    return null;
                });
                try {
                    assertTrue(secondBroadcastStarted.await(3, TimeUnit.SECONDS));
                    try {
                        second.get(500, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException | ExecutionException deferred) {
                        // 직렬화되면 대기할 수 있다. 실패 여부는 전송 해제 후 get에서 확인한다.
                    }
                } finally {
                    releaseFirstSend.countDown();
                }
                first.get(3, TimeUnit.SECONDS);
                second.get(3, TimeUnit.SECONDS);
                assertEquals(List.of("첫 방송", "두 번째 방송"), List.copyOf(received));
            } finally {
                releaseFirstSend.countDown();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"11:29:59, true", "11:30:00, false", "11:30:01, false"})
    void broadcastsOnlyBeforeEachConnectionsOperatingPeriodEnds(
            String currentTime, boolean aliceShouldReceive) throws IOException {
        RoomSessionRegistry registry = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);
        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        when(bob.getAttributes()).thenReturn(Map.of("closesAt",
                LocalDateTime.of(2026, 10, 12, 12, 30)));
        registry.register(1, alice);
        registry.register(1, bob);
        clock.set(CLOSES_AT.toLocalDate().atTime(LocalTime.parse(currentTime)));
        TextMessage message = new TextMessage("운영 구간 경계의 방송");

        // 정기 종료 작업을 호출하지 않아도 방송 자체가 만료 연결을 걸러야 한다.
        registry.broadcast(1, message);

        if (aliceShouldReceive) {
            verify(alice).sendMessage(message);
        } else {
            verify(alice, never()).sendMessage(any(TextMessage.class));
        }
        verify(bob).sendMessage(message);
    }

    @Test
    void skippedExpiredConnectionStillReceivesItsScheduledClosingNotice() throws IOException {
        RoomSessionRegistry registry = new RoomSessionRegistry(clock);
        WebSocketSession alice = mock(WebSocketSession.class);
        when(alice.isOpen()).thenReturn(true);
        when(alice.getAttributes()).thenReturn(Map.of("closesAt", CLOSES_AT));
        registry.register(1, alice);
        clock.set(CLOSES_AT);
        TextMessage notice = new TextMessage(
                "{\"type\":\"ERROR\",\"status\":403,\"message\":\"지금은 닫힌 방입니다.\"}");

        registry.broadcast(1, new TextMessage("종료 후 전달하지 않을 방송"));
        registry.closeExpiredSessions(CLOSES_AT, notice);

        verify(alice, times(1)).sendMessage(any(TextMessage.class));
        verify(alice).sendMessage(notice);
        verify(alice).close(CloseStatus.POLICY_VIOLATION);
    }
}
