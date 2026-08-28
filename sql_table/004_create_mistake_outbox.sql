BEGIN;

CREATE TABLE IF NOT EXISTS mistake_outbox_events (
    id UUID PRIMARY KEY,
    mistake_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_mistake_outbox_mistake
        FOREIGN KEY (mistake_id) REFERENCES personal_questions(id),
    CONSTRAINT ck_mistake_outbox_event_type
        CHECK (length(trim(event_type)) > 0),
    CONSTRAINT ck_mistake_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_mistake_outbox_attempts
        CHECK (attempts >= 0),
    CONSTRAINT ck_mistake_outbox_publish_state
        CHECK (
            (status = 'PENDING' AND published_at IS NULL)
            OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_mistake_outbox_pending
    ON mistake_outbox_events (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_mistake_outbox_mistake
    ON mistake_outbox_events (mistake_id);

COMMIT;
