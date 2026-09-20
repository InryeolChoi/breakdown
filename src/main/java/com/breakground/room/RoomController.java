package com.breakground.room;

import com.breakground.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    @GetMapping("/rooms")
    public List<RoomResponse> getRooms() {
        LocalDateTime now = LocalDateTime.now();
        return roomService.findOpenRooms(now);
    }
}
