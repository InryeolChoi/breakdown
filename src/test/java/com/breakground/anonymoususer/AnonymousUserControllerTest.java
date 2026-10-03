package com.breakground.anonymoususer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonymousUserController.class)
class AnonymousUserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnonymousUserService anonymousUserService;

    @Test
    void acceptsMissingNicknameAndPassesNullToService() throws Exception {
        when(anonymousUserService.addNewAnonymousUser(null)).thenReturn(
                new AnonymousUser(
                        "졸린고양이123",
                        LocalDate.of(2026, 10, 4),
                        LocalDateTime.of(2026, 10, 4, 1, 0)
                )
        );

        mockMvc.perform(post("/anonymoususer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("졸린고양이123"));

        verify(anonymousUserService).addNewAnonymousUser(null);
    }

    @Test
    void rejectsNicknameLongerThanThirtyCharactersBeforeCallingService() throws Exception {
        String requestBody = "{\"nickname\":\"" + "a".repeat(31) + "\"}";

        mockMvc.perform(post("/anonymoususer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(anonymousUserService);
    }
}
