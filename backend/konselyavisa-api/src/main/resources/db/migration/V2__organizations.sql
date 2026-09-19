CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Application role is not a superuser: FORCE RLS is otherwise bypassed by POSTGRES_USER.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'konselyavisa_app') THEN
        CREATE ROLE konselyavisa_app LOGIN PASSWORD 'konselyavisa' NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO konselyavisa_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO konselyavisa_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO konselyavisa_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO konselyavisa_app;

CREATE OR REPLACE FUNCTION app_current_org() RETURNS uuid
    LANGUAGE sql
    STABLE
AS $$
    SELECT NULLIF(btrim(current_setting('app.current_org', true)), '')::uuid;
$$;

CREATE OR REPLACE FUNCTION app_is_platform_admin() RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT lower(current_setting('app.is_platform_admin', true)) IN ('true', 't', '1');
$$;

CREATE TABLE organizations (
    id              UUID PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    name_i18n       JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    default_locale  VARCHAR(5)   NOT NULL DEFAULT 'fr',
    default_currency VARCHAR(3)  NOT NULL DEFAULT 'EUR',
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_organizations_code UNIQUE (code),
    CONSTRAINT uq_organizations_slug UNIQUE (slug),
    CONSTRAINT chk_organizations_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_organizations_name_i18n CHECK (jsonb_typeof(name_i18n) = 'object')
);

CREATE TABLE organization_settings (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    settings         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uq_organization_settings_org UNIQUE (organization_id),
    CONSTRAINT chk_organization_settings_json CHECK (jsonb_typeof(settings) = 'object')
);

INSERT INTO organizations (
    id, code, slug, name_i18n, status, default_locale, default_currency,
    created_at, updated_at, created_by, updated_by
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'DEMO',
    'demo',
    '{"fr": "Organisation démo", "pt": "Organização demo", "en": "Demo organization"}'::jsonb,
    'ACTIVE',
    'fr',
    'EUR',
    NOW(),
    NOW(),
    'flyway',
    'flyway'
);

INSERT INTO organization_settings (
    id, organization_id, settings, created_at, updated_at, created_by, updated_by
) VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '{"activeLanguages": ["fr", "pt", "en"], "defaultCurrency": "EUR"}'::jsonb,
    NOW(),
    NOW(),
    'flyway',
    'flyway'
);

ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organizations
    USING (app_is_platform_admin() OR id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR id = app_current_org());

ALTER TABLE organization_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organization_settings
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON organizations, organization_settings TO konselyavisa_app;
GRANT EXECUTE ON FUNCTION app_current_org() TO konselyavisa_app;
GRANT EXECUTE ON FUNCTION app_is_platform_admin() TO konselyavisa_app;
