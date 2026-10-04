CREATE TABLE report (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_event_id uuid NOT NULL,
    reporter_user_id integer NOT NULL REFERENCES anonymous_user(id),
    reported_user_id integer NOT NULL REFERENCES anonymous_user(id),
    room_id integer NOT NULL REFERENCES room(id),
    reason varchar(500) NOT NULL CHECK (btrim(reason) <> ''),
    evidence_content varchar(140) NOT NULL CHECK (btrim(evidence_content) <> ''),
    message_sent_at timestamp NOT NULL,
    created_at timestamp NOT NULL,
    evidence_expires_at timestamp NOT NULL,
    CONSTRAINT uq_report_reporter_message UNIQUE (reporter_user_id, message_event_id),
    CONSTRAINT ck_report_not_self CHECK (reporter_user_id <> reported_user_id)
);

CREATE INDEX ix_report_evidence_expires_at ON report(evidence_expires_at);
CREATE INDEX ix_report_reported_user_id ON report(reported_user_id);
