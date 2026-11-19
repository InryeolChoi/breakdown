package com.breakground.room;

import com.breakground.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final Clock clock;

    @GetMapping("/rooms")
    public List<RoomResponse> getRooms() {
        LocalDateTime now = LocalDateTime.now(clock);
        return roomService.findOpenRooms(now);
    }
}
