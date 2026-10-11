package com.breakground.chat.dto;

public record ChatErrorResponse(String type, int status, String message) {
}
