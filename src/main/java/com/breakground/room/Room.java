package com.breakground.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", length = 10, nullable = false)
    private String name;

    @Column(name = "open_at", nullable = false)
    private LocalTime openAt;

    @Column(name = "close_at", nullable = false)
    private LocalTime closeAt;

    @Column(name = "weekday_open", nullable = false)
    private boolean weekdayOpen;

    @Column(name = "weekend_open", nullable = false)
    private boolean weekendOpen;

    public Room(String name, LocalTime openAt, LocalTime closeAt, boolean weekdayOpen, boolean weekendOpen)
    {
        this.name = name;
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.weekdayOpen = weekdayOpen;
        this.weekendOpen = weekendOpen;
    }

    protected Room() {
    }

    public boolean isOpen(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (openAt.equals(closeAt)) {
            return false;
        }

        if (openAt.isBefore(closeAt)) {
            return isOpenOn(date)
                    && !time.isBefore(openAt)
                    && time.isBefore(closeAt);
        }

        if (!time.isBefore(openAt)) {
            return isOpenOn(date);
        }

        return time.isBefore(closeAt) && isOpenOn(date.minusDays(1));
    }

    private boolean isOpenOn(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

        return isWeekend ? weekendOpen : weekdayOpen;
    }
}
