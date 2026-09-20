package com.breakground.room;

import com.breakground.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;

    public List<RoomResponse> findOpenRooms(LocalDateTime now) {
        return roomRepository.findAll()
                .stream()
                .filter(room -> room.isOpen(now))
                .map(RoomResponse::new)
                .toList();
    }
}
