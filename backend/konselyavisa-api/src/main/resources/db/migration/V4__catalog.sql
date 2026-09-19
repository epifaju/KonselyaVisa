CREATE TABLE countries (
    id          UUID PRIMARY KEY,
    iso_code    VARCHAR(2)   NOT NULL,
    name_i18n   JSONB        NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uq_countries_iso_code UNIQUE (iso_code),
    CONSTRAINT chk_countries_name_i18n CHECK (jsonb_typeof(name_i18n) = 'object')
);

CREATE TABLE procedure_definitions (
    id                      UUID PRIMARY KEY,
    code                    VARCHAR(80)  NOT NULL,
    origin_country_id       UUID         NOT NULL REFERENCES countries (id),
    destination_country_id  UUID         NOT NULL REFERENCES countries (id),
    category                VARCHAR(50)  NOT NULL,
    name_i18n               JSONB        NOT NULL,
    description_i18n        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    CONSTRAINT uq_procedure_definitions_code UNIQUE (code),
    CONSTRAINT chk_procedure_definitions_category
        CHECK (category IN ('VISA', 'EVISA', 'LEGALIZATION', 'APOSTILLE', 'TRANSLATION', 'INSURANCE', 'APPOINTMENT')),
    CONSTRAINT chk_procedure_definitions_name CHECK (jsonb_typeof(name_i18n) = 'object'),
    CONSTRAINT chk_procedure_definitions_description CHECK (jsonb_typeof(description_i18n) = 'object')
);

CREATE TABLE procedure_versions (
    id                       UUID PRIMARY KEY,
    procedure_definition_id  UUID         NOT NULL REFERENCES procedure_definitions (id),
    version_number           INTEGER      NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    eligibility_rules        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    document_requirements    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    published_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),
    CONSTRAINT uq_procedure_versions UNIQUE (procedure_definition_id, version_number),
    CONSTRAINT chk_procedure_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_procedure_versions_eligibility CHECK (jsonb_typeof(eligibility_rules) = 'object'),
    CONSTRAINT chk_procedure_versions_requirements CHECK (jsonb_typeof(document_requirements) = 'array')
);

CREATE INDEX idx_procedure_definitions_route
    ON procedure_definitions (origin_country_id, destination_country_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON countries, procedure_definitions, procedure_versions TO konselyavisa_app;
