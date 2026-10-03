CREATE TYPE user_status AS ENUM ('ACTIVE', 'BANNED');

CREATE TABLE anonymous_user (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nickname varchar(10) NOT NULL,
    created_at date NOT NULL,
    last_seen_at timestamp NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE'
);
