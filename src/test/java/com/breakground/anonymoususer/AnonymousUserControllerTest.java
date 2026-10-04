package com.breakground.anonymoususer;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonymousUserController.class)
class AnonymousUserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnonymousUserService anonymousUserService;

    @Test
    void acceptsMissingNicknameAndPassesNullToService() throws Exception {
        when(anonymousUserService.getOrCreateAnonymousUser(null, null)).thenReturn(
                new AnonymousUserSession(new AnonymousUser(
                    "졸린고양이123",
                    LocalDate.of(2026, 10, 4),
                    LocalDateTime.of(2026, 10, 4, 1, 0),
                    "8f9b3cf2e4a1a5b8e90123456789abcdef0123456789abcdef0123456789abcd"
                ), "test-token")
        );

        mockMvc.perform(post("/anonymoususer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("anonymous_user_token=test-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=604800")))
                .andExpect(jsonPath("$.rawToken").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.nickname").value("졸린고양이123"));

        verify(anonymousUserService).getOrCreateAnonymousUser(null, null);
    }

    @Test
    void rejectsNicknameLongerThanThirtyCharactersBeforeCallingService() throws Exception {
        String requestBody = "{\"nickname\":\"" + "a".repeat(31) + "\"}";

        mockMvc.perform(post("/anonymoususer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이름은 30자 이하로 입력해주세요."));

        verifyNoInteractions(anonymousUserService);
    }
    @Test
    void forwardsCookieTokenAndSetsSecureCookieForHttps() throws Exception {
        when(anonymousUserService.getOrCreateAnonymousUser(null, "existing-token"))
                .thenReturn(session("existing-token"));

        mockMvc.perform(post("/anonymoususer")
                        .secure(true)
                        .cookie(new Cookie("anonymous_user_token", "existing-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("anonymous_user_token=existing-token")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")));

        verify(anonymousUserService).getOrCreateAnonymousUser(null, "existing-token");
    }

    @Test
    void acceptsThirtyCharacterNickname() throws Exception {
        String nickname = "가".repeat(30);
        when(anonymousUserService.getOrCreateAnonymousUser(nickname, null))
                .thenReturn(session("new-token"));

        mockMvc.perform(post("/anonymoususer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());

        verify(anonymousUserService).getOrCreateAnonymousUser(nickname, null);
    }

    @Test
    void rejectsMalformedJsonBeforeCallingService() throws Exception {
        mockMvc.perform(post("/anonymoususer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(anonymousUserService);
    }

    private AnonymousUserSession session(String rawToken) {
        return new AnonymousUserSession(new AnonymousUser(
                "오리", LocalDate.of(2026, 10, 5),
                LocalDateTime.of(2026, 10, 5, 0, 0), "a".repeat(64)), rawToken);
    }

}
