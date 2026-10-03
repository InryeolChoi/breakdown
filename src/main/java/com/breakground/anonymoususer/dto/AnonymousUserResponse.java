package com.breakground.anonymoususer.dto;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.anonymoususer.UserStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class AnonymousUserResponse {
    private Integer id;
    private String nickname;
    private LocalDate createdAt;
    private LocalDateTime lastSeenAt;
    private UserStatus status;

    public AnonymousUserResponse(AnonymousUser anonymousUser) {
        this.id = anonymousUser.getId();
        this.nickname = anonymousUser.getNickname();
        this.createdAt = anonymousUser.getCreatedAt();
        this.lastSeenAt = anonymousUser.getLastSeenAt();
        this.status = anonymousUser.getStatus();
    }
}
