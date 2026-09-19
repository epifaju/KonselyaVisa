INSERT INTO procedure_definitions (
    id, code, origin_country_id, destination_country_id, category,
    name_i18n, description_i18n, active, created_at, updated_at, created_by, updated_by
) VALUES (
    'b1111111-1111-1111-1111-111111111111',
    'VISA_TOURISM_FR_GW',
    'a1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111112',
    'VISA',
    '{"fr": "Visa touristique Guinée-Bissau", "pt": "Visto de turismo Guiné-Bissau", "en": "Guinea-Bissau tourist visa"}'::jsonb,
    '{"fr": "Visa court séjour pour motif touristique.", "pt": "Visto de curta duração para turismo.", "en": "Short-stay visa for tourism."}'::jsonb,
    TRUE,
    NOW(), NOW(), 'flyway', 'flyway'
);

INSERT INTO procedure_versions (
    id, procedure_definition_id, version_number, status, eligibility_rules, document_requirements,
    published_at, created_at, updated_at, created_by, updated_by
) VALUES (
    'b1111111-1111-1111-1111-111111111112',
    'b1111111-1111-1111-1111-111111111111',
    1,
    'PUBLISHED',
    '{
        "and": [
            {">=": [{"var": "passportValidityMonths"}, 6]},
            {"in": [{"var": "nationality"}, ["GW", "PT", "FR"]]}
        ]
    }'::jsonb,
    '[
        {
            "code": "PASSPORT",
            "required": true,
            "labelI18n": {
                "fr": "Passeport (validité 6 mois)",
                "pt": "Passaporte (validade 6 meses)",
                "en": "Passport (6 months validity)"
            },
            "constraints": {"minValidityMonths": 6}
        },
        {
            "code": "PHOTO",
            "required": true,
            "labelI18n": {
                "fr": "Photo d''identité",
                "pt": "Fotografia de identidade",
                "en": "Identity photograph"
            }
        }
    ]'::jsonb,
    NOW(), NOW(), NOW(), 'flyway', 'flyway'
);
