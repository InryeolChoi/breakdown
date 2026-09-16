package com.breakground.room;
import lombok.Getter;

import java.time.LocalTime;

@Getter
public class Room {
    private Integer id;
    private String name;
    private LocalTime openAt;
    private LocalTime closeAt;
}
