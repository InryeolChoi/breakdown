ALTER TABLE ROOM
ADD CONSTRAINT time_check
CHECK (open_at <> close_at);