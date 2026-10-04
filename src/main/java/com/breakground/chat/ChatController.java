package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUserCookies;
import com.breakground.chat.dto.ChatMessageResponse;
import com.breakground.chat.dto.MessageCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rooms/{roomId}/messages")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> create(
            @PathVariable Integer roomId, @Valid @RequestBody MessageCreateRequest request,
            @CookieValue(value = AnonymousUserCookies.NAME, required = false) String rawToken,
            HttpServletRequest servletRequest) {
        ChatMessage message = service.create(roomId, rawToken, request.content());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, AnonymousUserCookies.create(rawToken, servletRequest.isSecure()).toString())
                .body(ChatMessageResponse.from(message));
    }
}
