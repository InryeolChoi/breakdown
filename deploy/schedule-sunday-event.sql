\set ON_ERROR_STOP on

BEGIN;
LOCK TABLE room IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO room (name, open_at, close_at, weekday_open, weekend_open, event_starts_at, event_ends_at)
SELECT '특별 이벤트', TIME '00:00', TIME '00:00', FALSE, FALSE,
       TIMESTAMP '2026-11-29 00:00:00', TIMESTAMP '2026-11-30 00:00:00'
WHERE NOT EXISTS (
    SELECT 1 FROM room
    WHERE name = '특별 이벤트'
      AND event_starts_at = TIMESTAMP '2026-11-29 00:00:00'
      AND event_ends_at = TIMESTAMP '2026-11-30 00:00:00'
);

SELECT id, name, event_starts_at, event_ends_at
FROM room
WHERE name = '특별 이벤트'
  AND event_starts_at = TIMESTAMP '2026-11-29 00:00:00'
  AND event_ends_at = TIMESTAMP '2026-11-30 00:00:00';

COMMIT;
