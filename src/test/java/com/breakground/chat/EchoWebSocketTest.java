package com.breakground.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EchoWebSocketTest {

    @LocalServerPort
    private int port;

    @Test
    public void testEcho() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/echo");

        CompletableFuture<String> received = new CompletableFuture<>();
        StringBuilder buffer = new StringBuilder();

        CompletableFuture<WebSocket> webSocketFuture = client.newWebSocketBuilder()
            .buildAsync(uri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("Connected to WebSocket");
                        webSocket.request(1); // 다음 메시지 수신 요청
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        buffer.append(data);
                        if (last) {
                            received.complete(buffer.toString());
                            buffer.setLength(0);
                        }

                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("Closed: " + reason);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        received.completeExceptionally(error);
                    }
                }
            );

        WebSocket webSocket = webSocketFuture.get(3, TimeUnit.SECONDS);
        try {
            webSocket.sendText("Hello Echo", true)
                .get(3, TimeUnit.SECONDS);

            String actual = received.get(3, TimeUnit.SECONDS);
            assertEquals("Hello Echo", actual);
        } finally {
            webSocket.abort();
        }
    }
}
