package com.breakground.anonymoususer;

import com.breakground.anonymoususer.dto.AnonymousUserCreateRequest;
import com.breakground.anonymoususer.dto.AnonymousUserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class AnonymousUserController {
    private final AnonymousUserService anonymousUserService;

    @PostMapping("/anonymoususer")
    public AnonymousUserResponse createAnonymousUser (
        @Valid @RequestBody AnonymousUserCreateRequest request)
    {
        return new AnonymousUserResponse(
            anonymousUserService.addNewAnonymousUser(request.getNickname()));
    }

}
