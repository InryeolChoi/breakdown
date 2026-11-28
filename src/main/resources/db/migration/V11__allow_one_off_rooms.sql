ALTER TABLE room
    ADD COLUMN event_starts_at timestamp,
    ADD COLUMN event_ends_at timestamp,
    DROP CONSTRAINT time_check,
    DROP CONSTRAINT room_has_operating_day,
    ADD CONSTRAINT room_valid_schedule CHECK (
        (event_starts_at IS NULL AND event_ends_at IS NULL
            AND (weekday_open OR weekend_open) AND open_at <> close_at)
        OR
        (event_starts_at IS NOT NULL AND event_ends_at IS NOT NULL
            AND NOT weekday_open AND NOT weekend_open AND event_starts_at < event_ends_at
            AND open_at = CAST(event_starts_at AS time) AND close_at = CAST(event_ends_at AS time))
    );
