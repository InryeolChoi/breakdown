package com.breakground.anonymoususer.dto;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
public class AnonymousUserCreateRequest {
    @Size(max = 30)
    private String nickname;

    public AnonymousUserCreateRequest(String nickname)
    {
        this.nickname = nickname;
    }
    protected AnonymousUserCreateRequest() {}
}
