package com.breakground.room;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class RoomTest {

    @Test
    void allDayEventOpensOnlyOnItsScheduledDate() {
        LocalDate date = LocalDate.of(2026, 11, 29);
        LocalDateTime opensAt = date.atStartOfDay();
        LocalDateTime closesAt = date.plusDays(1).atStartOfDay();
        Room room = Room.event("특별 이벤트", opensAt, closesAt);

        assertFalse(room.isOpen(opensAt.minusNanos(1)));
        assertTrue(room.isOpen(opensAt));
        assertTrue(room.isOpen(closesAt.minusNanos(1)));
        org.junit.jupiter.api.Assertions.assertEquals(closesAt, room.currentClosingTime(opensAt));
        assertFalse(room.isOpen(closesAt));
        assertFalse(room.isOpen(opensAt.plusWeeks(1)));
    }

    @Test
    public void testRoom() {
        Room room = new Room("점심방",
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                true, false);
        assertTrue(room.isOpen(LocalDateTime.of(2026,
                9, 21, 10, 0)));
    }

    @Test
    public void testRoom2() {
        Room room = new Room("점심방",
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                true, false);
        assertFalse(room.isOpen(LocalDateTime.of(2026,
                9, 21, 11, 30)));
    }

    @Test
    public void testRoom3() {
        Room room = new Room("야근방",
                LocalTime.of(21, 0),
                LocalTime.of(2, 0),
                true, false);
        assertTrue(room.isOpen(LocalDateTime.of(
                2026, 9, 25, 21, 00)));
    }

    @Test
    public void opensAfterMidnightWhenOpenedOnPreviousWeekday() {
        Room room = new Room("야근방",
                LocalTime.of(21, 0),
                LocalTime.of(2, 0),
                true, false);

        assertTrue(room.isOpen(LocalDateTime.of(
                2026, 9, 26, 1, 0)));
    }

    @Test
    public void closesAtEndTimeAfterMidnight() {
        Room room = new Room("야근방",
                LocalTime.of(21, 0),
                LocalTime.of(2, 0),
                true, false);

        assertFalse(room.isOpen(LocalDateTime.of(
                2026, 9, 26, 2, 0)));
    }

    @Test
    public void doesNotOpenOnWeekend() {
        Room room = new Room("야근방",
                LocalTime.of(21, 0),
                LocalTime.of(2, 0),
                true, false);

        assertFalse(room.isOpen(LocalDateTime.of(
                2026, 9, 26, 21, 0)));
    }

    @Test
    public void closingTimeFollowsOvernightOperatingPeriod() {
        Room room = new Room("야근방", LocalTime.of(21, 0), LocalTime.of(2, 0), true, false);
        LocalDateTime closing = LocalDateTime.of(2026, 9, 26, 2, 0);
        org.junit.jupiter.api.Assertions.assertEquals(closing,
                room.currentClosingTime(LocalDateTime.of(2026, 9, 25, 23, 0)));
        org.junit.jupiter.api.Assertions.assertEquals(closing,
                room.currentClosingTime(LocalDateTime.of(2026, 9, 26, 1, 0)));
    }
}
