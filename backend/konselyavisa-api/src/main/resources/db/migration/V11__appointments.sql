CREATE TABLE appointment_slots (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    starts_at           TIMESTAMPTZ  NOT NULL,
    ends_at             TIMESTAMPTZ  NOT NULL,
    capacity            INTEGER      NOT NULL DEFAULT 1,
    location_i18n       JSONB        NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT chk_appointment_slots_window CHECK (ends_at > starts_at),
    CONSTRAINT chk_appointment_slots_capacity CHECK (capacity > 0),
    CONSTRAINT chk_appointment_slots_status CHECK (status IN ('OPEN', 'CANCELLED')),
    CONSTRAINT chk_appointment_slots_location CHECK (jsonb_typeof(location_i18n) = 'object')
);

CREATE INDEX idx_appointment_slots_org_start
    ON appointment_slots (organization_id, starts_at)
    WHERE status = 'OPEN';

CREATE TABLE appointments (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    case_id             UUID         NOT NULL REFERENCES cases (id),
    slot_id             UUID         NOT NULL REFERENCES appointment_slots (id),
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT chk_appointments_status CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED'))
);

CREATE UNIQUE INDEX uq_appointments_case_booked
    ON appointments (case_id)
    WHERE status = 'BOOKED';

CREATE INDEX idx_appointments_slot_booked
    ON appointments (slot_id)
    WHERE status = 'BOOKED';

ALTER TABLE appointment_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment_slots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointment_slots
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointments
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON appointment_slots, appointments TO konselyavisa_app;

INSERT INTO appointment_slots (
    id, organization_id, starts_at, ends_at, capacity, location_i18n, status,
    created_at, updated_at, created_by, updated_by
) VALUES (
    'c1111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    NOW() + INTERVAL '7 days',
    NOW() + INTERVAL '7 days 30 minutes',
    2,
    '{"fr": "Consulat — Guinée-Bissau", "pt": "Consulado — Guiné-Bissau", "en": "Consulate — Guinea-Bissau"}'::jsonb,
    'OPEN',
    NOW(), NOW(), 'flyway', 'flyway'
);
