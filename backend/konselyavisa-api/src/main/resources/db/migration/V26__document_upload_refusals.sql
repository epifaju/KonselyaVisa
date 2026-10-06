CREATE TABLE document_upload_refusals (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    case_id             UUID         NOT NULL REFERENCES cases (id),
    requirement_code    VARCHAR(50),
    reason_key          VARCHAR(120) NOT NULL,
    content_type        VARCHAR(100),
    size_bytes          BIGINT,
    sha256              VARCHAR(64),
    original_filename   VARCHAR(255),
    attempted_by        VARCHAR(100),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT chk_document_upload_refusals_sha256
        CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_document_upload_refusals_size
        CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE INDEX idx_document_upload_refusals_case
    ON document_upload_refusals (case_id, created_at DESC);
CREATE INDEX idx_document_upload_refusals_org_created
    ON document_upload_refusals (organization_id, created_at DESC);

ALTER TABLE document_upload_refusals ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_upload_refusals FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_upload_refusals
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON document_upload_refusals TO konselyavisa_app;
