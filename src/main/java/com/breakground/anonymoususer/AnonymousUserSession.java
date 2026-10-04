package com.breakground.anonymoususer;

// Service와 Controller 사이의 전달 값. HTTP 응답 본문으로 반환하지 않는다.
public record AnonymousUserSession(AnonymousUser user, String rawToken) {
    @Override
    public String toString() {
        return "AnonymousUserSession[userId=" + user.getId() + ", rawToken=REDACTED]";
    }
}
