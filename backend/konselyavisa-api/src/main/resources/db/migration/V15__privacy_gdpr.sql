CREATE TABLE privacy_consents (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organizations (id),
    applicant_id       UUID         REFERENCES applicants (id),
    case_id            UUID         REFERENCES cases (id),
    keycloak_subject   VARCHAR(100),
    purpose            VARCHAR(40)  NOT NULL,
    notice_version     VARCHAR(20)  NOT NULL,
    locale             VARCHAR(8)   NOT NULL,
    text_accepted      TEXT         NOT NULL,
    accepted_at        TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT chk_privacy_consents_purpose CHECK (purpose IN ('CASE_DEPOSIT'))
);

CREATE INDEX idx_privacy_consents_org_subject
    ON privacy_consents (organization_id, keycloak_subject, accepted_at DESC);
CREATE INDEX idx_privacy_consents_case ON privacy_consents (case_id);

CREATE TABLE data_deletion_requests (
    id                     UUID PRIMARY KEY,
    organization_id        UUID         NOT NULL REFERENCES organizations (id),
    keycloak_subject       VARCHAR(100) NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    requested_at           TIMESTAMPTZ  NOT NULL,
    scheduled_anonymize_at TIMESTAMPTZ  NOT NULL,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(100),
    updated_by             VARCHAR(100),
    CONSTRAINT chk_data_deletion_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_data_deletion_org_subject
    ON data_deletion_requests (organization_id, keycloak_subject, requested_at DESC);

ALTER TABLE privacy_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE privacy_consents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON privacy_consents
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

ALTER TABLE data_deletion_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_deletion_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON data_deletion_requests
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON privacy_consents, data_deletion_requests TO konselyavisa_app;
