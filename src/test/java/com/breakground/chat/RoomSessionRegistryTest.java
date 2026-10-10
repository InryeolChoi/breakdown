package com.breakground.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RoomSessionRegistryTest {

    @Test
    void broadcastsToBothConnectionsRegisteredInTheSameRoom() throws IOException {
        RoomSessionRegistry registry = new RoomSessionRegistry();
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);
        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);

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
        RoomSessionRegistry A = new RoomSessionRegistry();
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);

        when(alice.isOpen()).thenReturn(true);
        when(bob.isOpen()).thenReturn(true);

        A.register(1, alice);
        A.register(1, bob);

        A.unregister(1, alice);
        A.broadcast(1, new TextMessage("점심 뭐 먹지?"));

        verify(alice, never()).sendMessage(any(TextMessage.class));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(bob).sendMessage(sent.capture());
        assertEquals("점심 뭐 먹지?", sent.getValue().getPayload());
    }
}
