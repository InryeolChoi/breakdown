package com.breakground.report;

import com.breakground.anonymoususer.AnonymousUser;
import com.breakground.chat.ChatMessage;
import com.breakground.room.Room;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "report")
@Getter
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "message_event_id", nullable = false)
    private UUID messageEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private AnonymousUser reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private AnonymousUser reportedUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Column(name = "evidence_content", length = 140, nullable = false)
    private String evidenceContent;

    @Column(name = "message_sent_at", nullable = false)
    private LocalDateTime messageSentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "evidence_expires_at", nullable = false)
    private LocalDateTime evidenceExpiresAt;

    protected Report() {}

    public Report(AnonymousUser reporter, AnonymousUser reportedUser, Room room,
                  ChatMessage evidence, String reason, LocalDateTime now, LocalDateTime expiresAt) {
        this.reporter = reporter;
        this.reportedUser = reportedUser;
        this.room = room;
        this.messageEventId = evidence.id();
        this.reason = reason;
        this.evidenceContent = evidence.content();
        this.messageSentAt = evidence.sentAt();
        this.createdAt = now;
        this.evidenceExpiresAt = expiresAt;
    }
}
