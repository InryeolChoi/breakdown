ALTER TABLE anonymous_user
    ADD COLUMN token_hash varchar(64) NOT NULL;

ALTER TABLE anonymous_user
    ADD CONSTRAINT uq_anonymous_user_token_hash UNIQUE (token_hash);
