package com.breakground.chat;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class MessageCleanup {
    private final MessageRepository messages;
    private final Clock clock;

    public MessageCleanup(MessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${chat.cleanup-interval-ms:60000}")
    @Transactional
    public void removeReadyForCleanup() {
        messages.deleteReadyForCleanup(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
    }
}
