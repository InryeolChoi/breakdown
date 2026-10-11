package com.breakground.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import com.breakground.chat.EchoWebSocketHandler;
import com.breakground.chat.ChatWebSocketHandler;
import com.breakground.chat.ChatHandshakeInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final EchoWebSocketHandler echoWebSocketHandler;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoWebSocketHandler, "/ws/echo")
            .setAllowedOriginPatterns("*");
        registry.addHandler(chatWebSocketHandler, "/ws/rooms/{roomId}")
                .addInterceptors(chatHandshakeInterceptor);
    }
}
