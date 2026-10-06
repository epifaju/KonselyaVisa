-- eVisa, travel insurance, sworn translation — end-to-end demo slice (FR → GW),
-- gated like legalization/apostille: global OFF, demo org ON.

INSERT INTO procedure_definitions (
    id, code, origin_country_id, destination_country_id, category,
    name_i18n, description_i18n, active, created_at, updated_at, created_by, updated_by
) VALUES (
    'b4444444-4444-4444-4444-444444444441',
    'EVISA_TOURISM_FR_GW',
    'a1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111112',
    'EVISA',
    '{"fr": "eVisa touristique Guinée-Bissau", "pt": "eVisa de turismo Guiné-Bissau", "en": "Guinea-Bissau tourist eVisa"}'::jsonb,
    '{"fr": "Autorisation électronique de voyage pour un séjour touristique court.", "pt": "Autorização eletrónica de viagem para turismo de curta duração.", "en": "Electronic travel authorization for a short tourist stay."}'::jsonb,
    TRUE,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'b5555555-5555-5555-5555-555555555551',
    'TRAVEL_INSURANCE_FR_GW',
    'a1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111112',
    'INSURANCE',
    '{"fr": "Assurance voyage Guinée-Bissau", "pt": "Seguro de viagem Guiné-Bissau", "en": "Travel insurance Guinea-Bissau"}'::jsonb,
    '{"fr": "Couverture médicale et assistance pour un séjour en Guinée-Bissau (jusqu’à 90 jours).", "pt": "Cobertura médica e assistência para uma estadia na Guiné-Bissau (até 90 dias).", "en": "Medical cover and assistance for a stay in Guinea-Bissau (up to 90 days)."}'::jsonb,
    TRUE,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'b6666666-6666-6666-6666-666666666661',
    'SWORN_TRANSLATION_FR_GW',
    'a1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111112',
    'TRANSLATION',
    '{"fr": "Traduction assermentée (FR → PT)", "pt": "Tradução juramentada (FR → PT)", "en": "Sworn translation (FR → PT)"}'::jsonb,
    '{"fr": "Traduction assermentée français → portugais pour usage en Guinée-Bissau.", "pt": "Tradução juramentada francês → português para uso na Guiné-Bissau.", "en": "Sworn French → Portuguese translation for use in Guinea-Bissau."}'::jsonb,
    TRUE,
    NOW(), NOW(), 'flyway', 'flyway'
);

INSERT INTO procedure_versions (
    id, procedure_definition_id, version_number, status, eligibility_rules, document_requirements, pricing,
    estimated_instruction_days, published_at, created_at, updated_at, created_by, updated_by
) VALUES (
    'b4444444-4444-4444-4444-444444444442',
    'b4444444-4444-4444-4444-444444444441',
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
        },
        {
            "code": "TRAVEL_ITINERARY",
            "required": true,
            "labelI18n": {
                "fr": "Itinéraire / billet (aller-retour)",
                "pt": "Itinerário / bilhete (ida e volta)",
                "en": "Travel itinerary / return ticket"
            }
        }
    ]'::jsonb,
    '{"currency": "EUR", "amountMinor": 6500}'::jsonb,
    3,
    NOW(), NOW(), NOW(), 'flyway', 'flyway'
), (
    'b5555555-5555-5555-5555-555555555552',
    'b5555555-5555-5555-5555-555555555551',
    1,
    'PUBLISHED',
    '{
        "and": [
            {"in": [{"var": "nationality"}, ["FR", "PT", "GW"]]},
            {">=": [{"var": "tripDurationDays"}, 1]},
            {"<=": [{"var": "tripDurationDays"}, 90]}
        ]
    }'::jsonb,
    '[
        {
            "code": "PASSPORT",
            "required": true,
            "labelI18n": {
                "fr": "Passeport",
                "pt": "Passaporte",
                "en": "Passport"
            }
        },
        {
            "code": "TRIP_DETAILS",
            "required": true,
            "labelI18n": {
                "fr": "Dates et destination du séjour",
                "pt": "Datas e destino da estadia",
                "en": "Trip dates and destination"
            }
        }
    ]'::jsonb,
    '{"currency": "EUR", "amountMinor": 2500}'::jsonb,
    2,
    NOW(), NOW(), NOW(), 'flyway', 'flyway'
), (
    'b6666666-6666-6666-6666-666666666662',
    'b6666666-6666-6666-6666-666666666661',
    1,
    'PUBLISHED',
    '{
        "and": [
            {"in": [{"var": "documentType"}, ["BIRTH_CERTIFICATE", "DIPLOMA", "CRIMINAL_RECORD"]]},
            {"in": [{"var": "nationality"}, ["FR", "PT", "GW"]]},
            {"==": [{"var": "sourceLanguage"}, "fr"]},
            {"==": [{"var": "targetLanguage"}, "pt"]}
        ]
    }'::jsonb,
    '[
        {
            "code": "ORIGINAL_DOCUMENT",
            "required": true,
            "labelI18n": {
                "fr": "Document original à traduire",
                "pt": "Documento original a traduzir",
                "en": "Original document to translate"
            }
        },
        {
            "code": "ID_COPY",
            "required": true,
            "labelI18n": {
                "fr": "Copie d''une pièce d''identité",
                "pt": "Cópia de um documento de identidade",
                "en": "Copy of an identity document"
            }
        }
    ]'::jsonb,
    '{"currency": "EUR", "amountMinor": 5500}'::jsonb,
    8,
    NOW(), NOW(), NOW(), 'flyway', 'flyway'
);

INSERT INTO feature_flags (
    id, flag_key, organization_id, enabled, value_json, created_at, updated_at, created_by, updated_by
) VALUES (
    'd2222222-2222-2222-2222-222222222221',
    'procedure.EVISA_TOURISM_FR_GW',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd2222222-2222-2222-2222-222222222222',
    'procedure.EVISA_TOURISM_FR_GW',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd2222222-2222-2222-2222-222222222223',
    'procedure.TRAVEL_INSURANCE_FR_GW',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd2222222-2222-2222-2222-222222222224',
    'procedure.TRAVEL_INSURANCE_FR_GW',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd2222222-2222-2222-2222-222222222225',
    'procedure.SWORN_TRANSLATION_FR_GW',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd2222222-2222-2222-2222-222222222226',
    'procedure.SWORN_TRANSLATION_FR_GW',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
);
