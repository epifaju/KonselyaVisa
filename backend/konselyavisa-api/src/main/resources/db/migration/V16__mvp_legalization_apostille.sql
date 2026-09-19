INSERT INTO procedure_definitions (
    id, code, origin_country_id, destination_country_id, category,
    name_i18n, description_i18n, active, created_at, updated_at, created_by, updated_by
) VALUES (
    'b2222222-2222-2222-2222-222222222221',
    'LEGALIZATION_FR_GW',
    'a1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111112',
    'LEGALIZATION',
    '{"fr": "Légalisation de document (France → Guinée-Bissau)", "pt": "Legalização de documento (França → Guiné-Bissau)", "en": "Document legalization (France → Guinea-Bissau)"}'::jsonb,
    '{"fr": "Légalisation d’un acte pour usage en Guinée-Bissau.", "pt": "Legalização de um ato para uso na Guiné-Bissau.", "en": "Legalization of a document for use in Guinea-Bissau."}'::jsonb,
    TRUE,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'b3333333-3333-3333-3333-333333333331',
    'APOSTILLE_FR_PT',
    'a1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111113',
    'APOSTILLE',
    '{"fr": "Apostille (France → Portugal)", "pt": "Apostila (França → Portugal)", "en": "Apostille (France → Portugal)"}'::jsonb,
    '{"fr": "Apostille de La Haye pour un document destiné au Portugal.", "pt": "Apostila da Haia para um documento destinado a Portugal.", "en": "Hague apostille for a document destined for Portugal."}'::jsonb,
    TRUE,
    NOW(), NOW(), 'flyway', 'flyway'
);

INSERT INTO procedure_versions (
    id, procedure_definition_id, version_number, status, eligibility_rules, document_requirements, pricing,
    published_at, created_at, updated_at, created_by, updated_by
) VALUES (
    'b2222222-2222-2222-2222-222222222222',
    'b2222222-2222-2222-2222-222222222221',
    1,
    'PUBLISHED',
    '{
        "and": [
            {"in": [{"var": "documentType"}, ["BIRTH_CERTIFICATE", "DIPLOMA", "CRIMINAL_RECORD"]]},
            {"in": [{"var": "nationality"}, ["FR", "PT", "GW"]]}
        ]
    }'::jsonb,
    '[
        {
            "code": "ORIGINAL_DOCUMENT",
            "required": true,
            "labelI18n": {
                "fr": "Document original à légaliser",
                "pt": "Documento original a legalizar",
                "en": "Original document to legalize"
            }
        },
        {
            "code": "ID_COPY",
            "required": true,
            "labelI18n": {
                "fr": "Copie d’une pièce d’identité",
                "pt": "Cópia de um documento de identidade",
                "en": "Copy of an identity document"
            }
        }
    ]'::jsonb,
    '{"currency": "EUR", "amountMinor": 4500}'::jsonb,
    NOW(), NOW(), NOW(), 'flyway', 'flyway'
), (
    'b3333333-3333-3333-3333-333333333332',
    'b3333333-3333-3333-3333-333333333331',
    1,
    'PUBLISHED',
    '{
        "and": [
            {"==": [{"var": "hagueConvention"}, true]},
            {"in": [{"var": "nationality"}, ["FR", "PT"]]}
        ]
    }'::jsonb,
    '[
        {
            "code": "ORIGINAL_DOCUMENT",
            "required": true,
            "labelI18n": {
                "fr": "Document original à apostiller",
                "pt": "Documento original a apostilar",
                "en": "Original document for apostille"
            }
        },
        {
            "code": "PASSPORT",
            "required": true,
            "labelI18n": {
                "fr": "Passeport",
                "pt": "Passaporte",
                "en": "Passport"
            }
        }
    ]'::jsonb,
    '{"currency": "EUR", "amountMinor": 3500}'::jsonb,
    NOW(), NOW(), NOW(), 'flyway', 'flyway'
);

INSERT INTO feature_flags (
    id, flag_key, organization_id, enabled, value_json, created_at, updated_at, created_by, updated_by
) VALUES (
    'd1111111-1111-1111-1111-111111111111',
    'procedure.LEGALIZATION_FR_GW',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-111111111112',
    'procedure.LEGALIZATION_FR_GW',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-111111111113',
    'procedure.APOSTILLE_FR_PT',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-111111111114',
    'procedure.APOSTILLE_FR_PT',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
);
