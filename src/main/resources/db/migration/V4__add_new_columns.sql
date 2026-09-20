ALTER TABLE ROOM
    ADD COLUMN weekday_open BOOLEAN,
    ADD COLUMN weekend_open BOOLEAN;

UPDATE ROOM
SET weekday_open = TRUE,
    weekend_open = FALSE;

ALTER TABLE ROOM
    ALTER COLUMN weekday_open SET NOT NULL,
    ALTER COLUMN weekend_open SET NOT NULL,
    ADD CONSTRAINT room_has_operating_day CHECK (weekday_open OR weekend_open);
