package com.breakground.room;

import com.breakground.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    @GetMapping("/rooms")
    public List<RoomResponse> getRooms() {
        LocalTime now = LocalTime.now();
        return roomService.findOpenRooms(now);
    }
}
