ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE outbox_events
    ADD COLUMN last_error VARCHAR(200);

CREATE INDEX idx_outbox_events_due
    ON outbox_events (next_attempt_at)
    WHERE status = 'PENDING';
