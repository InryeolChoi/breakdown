package com.breakground.chat;

import com.breakground.chat.dto.ChatErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageCodecTest {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ChatMessageCodec codec = new ChatMessageCodec(mapper);

    @Test
    void decodesContentWithoutUsingClientSuppliedIdentity() {
        var request = codec.decode("""
                {"content":"점심 뭐 먹지?", "roomId":999, "authorId":999}
                """);

        assertEquals("점심 뭐 먹지?", request.content());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{", "null", "[]", "\"안녕\"", "{}",
            "{\"content\":null}", "{\"content\":123}", "{\"content\":true}",
            "{\"content\":[]}", "{\"content\":{}}", "{\"content\":\"안녕\"} {}"})
    void rejectsMalformedJsonOrNonStringContent(String payload) {
        var error = assertThrows(ResponseStatusException.class, () -> codec.decode(payload));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void leavesBlankAndLengthValidationToChatService() {
        assertEquals(" ", codec.decode("{\"content\":\" \"}").content());
        String content = "가".repeat(141);
        assertEquals(content, codec.decode("{\"content\":\"" + content + "\"}").content());
    }

    @Test
    void encodesServerMessageWithIdentityAndEscapedContent() {
        UUID id = UUID.fromString("9d5f1a70-0d19-45ac-8c48-534a70db4fe3");
        LocalDateTime sentAt = LocalDateTime.of(2026, 10, 11, 10, 30);
        String content = "\"안녕\"\n다음 줄";
        var saved = new ChatMessage(id, 1, 2, "오리", content, sentAt, sentAt.plusMinutes(15));

        JsonNode json = mapper.readTree(codec.encode(saved).getPayload());

        assertEquals(id.toString(), json.get("id").stringValue());
        assertEquals(1, json.get("roomId").intValue());
        assertEquals(2, json.get("authorId").intValue());
        assertEquals("오리", json.get("nickname").stringValue());
        assertEquals(content, json.get("content").stringValue());
        assertEquals(sentAt, mapper.treeToValue(json.get("sentAt"), LocalDateTime.class));
        assertEquals(sentAt.plusMinutes(15), mapper.treeToValue(json.get("expiresAt"), LocalDateTime.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    void encodesErrorDataWithTheExistingJsonFieldsAndEscapedMessage(int status) {
        var response = new ChatErrorResponse("ERROR", status, "\"안내\"\n다시 확인해주세요.");

        JsonNode json = mapper.readTree(codec.encodeError(response).getPayload());

        assertEquals(3, json.size());
        assertEquals("ERROR", json.get("type").stringValue());
        assertEquals(status, json.get("status").intValue());
        assertEquals("\"안내\"\n다시 확인해주세요.", json.get("message").stringValue());
    }
}
