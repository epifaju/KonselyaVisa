CREATE TABLE documents (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    case_id             UUID         NOT NULL REFERENCES cases (id),
    requirement_code    VARCHAR(50)  NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    sha256              VARCHAR(64)  NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    duplicate_hash      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_documents_status CHECK (status IN ('UPLOADED', 'REPLACED', 'REJECTED')),
    CONSTRAINT chk_documents_size CHECK (size_bytes > 0),
    CONSTRAINT chk_documents_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_documents_case ON documents (case_id, created_at);
CREATE INDEX idx_documents_org_sha256 ON documents (organization_id, sha256);

CREATE TABLE document_access_logs (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    document_id         UUID         NOT NULL REFERENCES documents (id),
    accessed_by         VARCHAR(100),
    action              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT chk_document_access_action CHECK (action IN ('DOWNLOAD'))
);

CREATE INDEX idx_document_access_document ON document_access_logs (document_id, created_at);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON documents
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

ALTER TABLE document_access_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_access_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_access_logs
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON documents, document_access_logs TO konselyavisa_app;
