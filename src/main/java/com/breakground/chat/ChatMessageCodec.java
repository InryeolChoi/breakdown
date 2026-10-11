package com.breakground.chat;

import com.breakground.chat.dto.ChatMessageResponse;
import com.breakground.chat.dto.ChatErrorResponse;
import com.breakground.chat.dto.MessageCreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChatMessageCodec {
    private final JsonMapper mapper;
    private final ObjectReader reader;

    public ChatMessageCodec(JsonMapper mapper) {
        this.mapper = mapper;
        this.reader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public MessageCreateRequest decode(String payload) {
        if (payload == null) {
            throw invalidRequest();
        }
        JsonNode root;
        try {
            root = reader.readTree(payload);
        } catch (JacksonException exception) {
            throw invalidRequest();
        }
        if (root == null || !root.isObject()) {
            throw invalidRequest();
        }
        JsonNode content = root.get("content");
        if (content == null || !content.isString()) {
            throw invalidRequest();
        }
        // 빈 본문·길이 등 비즈니스 검증은 기존 ChatService가 담당한다.
        return new MessageCreateRequest(content.stringValue());
    }

    public TextMessage encode(ChatMessage message) {
        return new TextMessage(mapper.writeValueAsString(ChatMessageResponse.from(message)));
    }

    public TextMessage encodeError(ChatErrorResponse response) {
        return new TextMessage(mapper.writeValueAsString(response));
    }

    private ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "content 문자열을 담은 JSON 객체를 보내주세요.");
    }
}
