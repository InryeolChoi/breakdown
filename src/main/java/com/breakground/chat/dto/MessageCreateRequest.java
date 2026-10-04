package com.breakground.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageCreateRequest(@NotBlank(message = "메시지를 입력해주세요.") String content) {
}
