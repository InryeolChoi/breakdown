package com.breakground.chat;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.room.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "message")
@Getter
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anonymous_user_id", nullable = false, updatable = false)
    private AnonymousUser author;

    @Column(name = "nickname", length = 30, nullable = false, updatable = false)
    private String nickname;

    @Column(name = "content", length = 140, nullable = false, updatable = false)
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "delete_after", nullable = false, updatable = false)
    private LocalDateTime deleteAfter;

    protected Message() {}

    public Message(Room room, AnonymousUser author, String content,
                   LocalDateTime sentAt, LocalDateTime expiresAt, LocalDateTime deleteAfter) {
        this.room = room;
        this.author = author;
        this.nickname = author.getNickname();
        this.content = content;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
        this.deleteAfter = deleteAfter;
    }
}
