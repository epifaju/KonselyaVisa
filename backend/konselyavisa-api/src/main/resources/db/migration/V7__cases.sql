CREATE TABLE applicants (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organizations (id),
    keycloak_subject   VARCHAR(100),
    email              VARCHAR(255),
    display_name       VARCHAR(200),
    facts              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT chk_applicants_facts CHECK (jsonb_typeof(facts) = 'object')
);

CREATE INDEX idx_applicants_org_subject
    ON applicants (organization_id, keycloak_subject);

CREATE TABLE cases (
    id                        UUID PRIMARY KEY,
    organization_id           UUID         NOT NULL REFERENCES organizations (id),
    applicant_id              UUID         NOT NULL REFERENCES applicants (id),
    procedure_definition_id   UUID         NOT NULL REFERENCES procedure_definitions (id),
    procedure_version_id      UUID         NOT NULL REFERENCES procedure_versions (id),
    reference                 VARCHAR(40)  NOT NULL,
    status                    VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
    applicant_facts           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    eligibility_passed        BOOLEAN      NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    created_by                VARCHAR(100),
    updated_by                VARCHAR(100),
    CONSTRAINT uq_cases_org_reference UNIQUE (organization_id, reference),
    CONSTRAINT chk_cases_status CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_cases_applicant_facts CHECK (jsonb_typeof(applicant_facts) = 'object')
);

CREATE INDEX idx_cases_org_status ON cases (organization_id, status);
CREATE INDEX idx_cases_applicant ON cases (applicant_id);
CREATE INDEX idx_cases_procedure_version ON cases (procedure_version_id);

ALTER TABLE applicants ENABLE ROW LEVEL SECURITY;
ALTER TABLE applicants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON applicants
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

ALTER TABLE cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE cases FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cases
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON applicants, cases TO konselyavisa_app;
