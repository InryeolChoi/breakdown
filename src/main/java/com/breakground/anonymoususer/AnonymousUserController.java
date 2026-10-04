package com.breakground.anonymoususer;

import com.breakground.anonymoususer.dto.AnonymousUserCreateRequest;
import com.breakground.anonymoususer.dto.AnonymousUserResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;

@RestController
@AllArgsConstructor
public class AnonymousUserController {
    private final AnonymousUserService anonymousUserService;

    @PostMapping("/anonymoususer")
    public ResponseEntity<AnonymousUserResponse> createAnonymousUser(
            @Valid @RequestBody AnonymousUserCreateRequest request,
            @CookieValue(value = "anonymous_user_token", required = false) String rawToken,
            HttpServletRequest servletRequest
    ) {
        AnonymousUserSession session = anonymousUserService.getOrCreateAnonymousUser(
                request.getNickname(), rawToken);
        ResponseCookie cookie = ResponseCookie.from("anonymous_user_token", session.rawToken())
                .httpOnly(true)
                .secure(servletRequest.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AnonymousUserResponse(session.user()));
    }
}
