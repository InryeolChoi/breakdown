CREATE TABLE message (
    id uuid PRIMARY KEY,
    room_id integer NOT NULL REFERENCES room(id),
    anonymous_user_id integer NOT NULL REFERENCES anonymous_user(id),
    nickname varchar(30) NOT NULL,
    content varchar(140) NOT NULL,
    sent_at timestamp NOT NULL,
    expires_at timestamp NOT NULL,
    delete_after timestamp NOT NULL,
    CONSTRAINT ck_message_nickname_not_blank CHECK (btrim(nickname) <> ''),
    CONSTRAINT ck_message_content_not_blank CHECK (btrim(content) <> ''),
    CONSTRAINT ck_message_expiration_after_sent CHECK (expires_at > sent_at),
    CONSTRAINT ck_message_deletion_after_expiration CHECK (delete_after > expires_at)
);

CREATE INDEX ix_message_delete_after ON message(delete_after);
