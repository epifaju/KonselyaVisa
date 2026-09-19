CREATE TABLE feature_flags (
    id               UUID PRIMARY KEY,
    flag_key         VARCHAR(100) NOT NULL,
    organization_id  UUID REFERENCES organizations (id),
    enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    value_json       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT chk_feature_flags_value CHECK (jsonb_typeof(value_json) = 'object')
);

CREATE UNIQUE INDEX uq_feature_flags_global_key
    ON feature_flags (flag_key)
    WHERE organization_id IS NULL;

CREATE UNIQUE INDEX uq_feature_flags_org_key
    ON feature_flags (organization_id, flag_key)
    WHERE organization_id IS NOT NULL;

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     UUID         NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    payload          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts         INTEGER      NOT NULL DEFAULT 0,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_outbox_events_payload CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_outbox_events_poll
    ON outbox_events (status, created_at)
    WHERE status = 'PENDING';

ALTER TABLE feature_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE feature_flags FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON feature_flags
    USING (
        app_is_platform_admin()
        OR organization_id IS NULL
        OR organization_id = app_current_org()
    )
    WITH CHECK (
        app_is_platform_admin()
        OR (organization_id IS NOT NULL AND organization_id = app_current_org())
    );

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON outbox_events
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON feature_flags, outbox_events TO konselyavisa_app;
