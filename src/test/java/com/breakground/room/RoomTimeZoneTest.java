package com.breakground.room;

import com.breakground.config.ChatConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated("Changes the JVM default time zone and restores it after the test")
class RoomTimeZoneTest {
    @Test
    void roomListUsesTheSameSeoulTimeAsChatWhenServerDefaultsToUtc() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Clock clock = new ChatConfiguration().applicationClock();
            LocalDateTime seoulNow = LocalDateTime.now(clock);
            Room room = new Room("시간대검증", seoulNow.minusMinutes(30).toLocalTime(),
                    seoulNow.plusMinutes(30).toLocalTime(), true, true);
            RoomRepository repository = mock(RoomRepository.class);
            when(repository.findAll()).thenReturn(List.of(room));
            RoomController controller = new RoomController(new RoomService(repository), clock);

            assertEquals(List.of("시간대검증"), controller.getRooms().stream()
                    .map(response -> response.getName()).toList(),
                    "A room open under the chat clock must also appear in GET /rooms on a UTC server");
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
