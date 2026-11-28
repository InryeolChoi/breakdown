package com.breakground.room.dto;

import com.breakground.room.Room;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
public class RoomResponse {
    private Integer id;
    private String name;
    private LocalTime openAt;
    private LocalTime closeAt;
    private LocalDateTime eventStartsAt;
    private LocalDateTime eventEndsAt;

    public RoomResponse(Room room) {
        this.id = room.getId();
        this.name = room.getName();
        this.openAt = room.getOpenAt();
        this.closeAt = room.getCloseAt();
        this.eventStartsAt = room.getEventStartsAt();
        this.eventEndsAt = room.getEventEndsAt();
    }
}
