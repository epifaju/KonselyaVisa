-- Retention purge index + per-organization DPA (art. 28) formalization.

CREATE INDEX idx_data_deletion_due
    ON data_deletion_requests (scheduled_anonymize_at)
    WHERE status = 'PENDING';

CREATE TABLE organization_dpa_agreements (
    id                      UUID PRIMARY KEY,
    organization_id         UUID         NOT NULL REFERENCES organizations (id),
    version                 VARCHAR(40)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    retention_days          INTEGER      NOT NULL,
    processor_legal_name    VARCHAR(200) NOT NULL,
    controller_name_i18n    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    summary_i18n            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    document_uri            VARCHAR(500),
    accepted_at             TIMESTAMPTZ  NOT NULL,
    accepted_by_label       VARCHAR(120),
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    CONSTRAINT chk_organization_dpa_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_organization_dpa_retention CHECK (retention_days >= 1 AND retention_days <= 3650),
    CONSTRAINT chk_organization_dpa_controller CHECK (jsonb_typeof(controller_name_i18n) = 'object'),
    CONSTRAINT chk_organization_dpa_summary CHECK (jsonb_typeof(summary_i18n) = 'object')
);

CREATE UNIQUE INDEX uq_organization_dpa_active
    ON organization_dpa_agreements (organization_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_organization_dpa_org
    ON organization_dpa_agreements (organization_id, accepted_at DESC);

ALTER TABLE organization_dpa_agreements ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_dpa_agreements FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organization_dpa_agreements
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON organization_dpa_agreements TO konselyavisa_app;

INSERT INTO organization_dpa_agreements (
    id, organization_id, version, status, retention_days,
    processor_legal_name, controller_name_i18n, summary_i18n, document_uri,
    accepted_at, accepted_by_label, created_at, updated_at, created_by, updated_by
) VALUES (
    'e1111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '2026.09',
    'ACTIVE',
    1825,
    'KonselyaVisa Platform Operator',
    '{
        "fr": "Consulat de Guinée-Bissau en France (démo)",
        "pt": "Consulado da Guiné-Bissau em França (demo)",
        "en": "Embassy of Guinea-Bissau in France (demo)"
    }'::jsonb,
    '{
        "fr": "Accord de sous-traitance (art. 28 RGPD) : l’opérateur héberge et traite les dossiers pour le compte du responsable de traitement organisationnel. Conservation des données d’identité : 1825 jours après demande d’effacement, puis anonymisation automatique.",
        "pt": "Acordo de subprocessamento (art. 28 RGPD): o operador alojará e tratará os processos por conta do responsável pelo tratamento. Conservação dos dados de identidade: 1825 dias após pedido de apagamento, depois anonimização automática.",
        "en": "Data processing agreement (GDPR art. 28): the operator hosts and processes cases on behalf of the organizational controller. Identity data retention: 1825 days after an erasure request, then automatic anonymisation."
    }'::jsonb,
    NULL,
    NOW(),
    'flyway-seed',
    NOW(), NOW(), 'flyway', 'flyway'
);
