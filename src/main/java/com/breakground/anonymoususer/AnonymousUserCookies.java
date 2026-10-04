package com.breakground.anonymoususer;

import org.springframework.http.ResponseCookie;
import java.time.Duration;

public final class AnonymousUserCookies {
    public static final String NAME = "anonymous_user_token";
    private AnonymousUserCookies() {}

    public static ResponseCookie create(String rawToken, boolean secure) {
        return ResponseCookie.from(NAME, rawToken).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(Duration.ofDays(7)).build();
    }
}
