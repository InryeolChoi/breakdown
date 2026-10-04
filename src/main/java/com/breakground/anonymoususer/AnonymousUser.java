package com.breakground.anonymoususer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_user")
@Getter
public class AnonymousUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nickname", length = 30, nullable = false)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    public AnonymousUser(String nickname, LocalDate createdAt, LocalDateTime lastSeenAt) {
        this.nickname = nickname;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
        this.status = UserStatus.ACTIVE;
    }

    protected AnonymousUser() {}
}
