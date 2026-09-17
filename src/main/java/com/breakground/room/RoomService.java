package com.breakground.room;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;

    public List<Room> findOpenRooms(LocalTime now) {
        return roomRepository.findAll()
                .stream()
                .filter(room -> room.isOpen(now))
                .toList();
    }
}
