package com.breakground.room;
import jakarta.persistence.*;
import lombok.Getter;

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
}
