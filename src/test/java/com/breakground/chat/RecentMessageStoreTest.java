package com.breakground.chat;

import com.breakground.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class RecentMessageStoreTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 5, 10, 30);
    private final MutableClock clock = new MutableClock(now);

    @Test
    void returnsOriginalUntilExactExpirationThenRemovesIt() {
        RecentMessageStore store = store(10);
        ChatMessage message = message(now.plusMinutes(15));
        store.add(message);
        clock.set(now.plusMinutes(15).minusNanos(1));
        assertEquals(message, store.find(message.id()).orElseThrow());
        clock.set(now.plusMinutes(15));
        assertTrue(store.find(message.id()).isEmpty());
    }

    @Test
    void expiredEntriesAreCleanedEvenWithoutAReportRequest() {
        RecentMessageStore store = store(1);
        store.add(message(now.plusMinutes(15)));
        clock.set(now.plusMinutes(15));
        store.removeExpired();
        ChatMessage replacement = message(now.plusMinutes(20));
        store.add(replacement);
        assertTrue(store.find(replacement.id()).isPresent());
    }

    @Test
    void doesNotSilentlyEvictReportableEvidenceAtCapacity() {
        RecentMessageStore store = store(1);
        ChatMessage first = message(now.plusMinutes(15));
        store.add(first);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> store.add(message(now.plusMinutes(15))));
        assertEquals(503, error.getStatusCode().value());
        assertTrue(store.find(first.id()).isPresent());
    }

    @Test
    void concurrentWritersCannotExceedTheCapacity() throws Exception {
        RecentMessageStore store = store(10);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 30).mapToObj(i ->
                    (java.util.concurrent.Callable<Boolean>) () -> {
                        try { store.add(message(now.plusMinutes(15))); return true; }
                        catch (ResponseStatusException e) { assertEquals(503, e.getStatusCode().value()); return false; }
                    }).toList();
            long accepted = 0;
            for (var result : executor.invokeAll(tasks)) {
                if (result.get()) accepted++;
            }
            assertEquals(10, accepted);
        }
    }

    private RecentMessageStore store(int capacity) {
        return new RecentMessageStore(new ChatProperties(Duration.ofMinutes(15), capacity), clock);
    }
    private ChatMessage message(LocalDateTime expiry) {
        return new ChatMessage(UUID.randomUUID(), 1, 2, "오리", "원본", now, expiry);
    }
}
