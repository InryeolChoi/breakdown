ALTER TABLE anonymous_user
    ADD COLUMN banned_until timestamp;

ALTER TABLE anonymous_user
    ADD CONSTRAINT ck_anonymous_user_ban_period
        CHECK (
            (status = 'ACTIVE' AND banned_until IS NULL)
            OR (status = 'BANNED' AND banned_until IS NOT NULL)
        );
