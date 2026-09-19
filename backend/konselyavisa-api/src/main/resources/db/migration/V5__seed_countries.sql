INSERT INTO countries (id, iso_code, name_i18n, active, created_at, updated_at, created_by, updated_by)
VALUES
    (
        'a1111111-1111-1111-1111-111111111111',
        'FR',
        '{"fr": "France", "pt": "França", "en": "France"}'::jsonb,
        TRUE, NOW(), NOW(), 'flyway', 'flyway'
    ),
    (
        'a1111111-1111-1111-1111-111111111112',
        'GW',
        '{"fr": "Guinée-Bissau", "pt": "Guiné-Bissau", "en": "Guinea-Bissau"}'::jsonb,
        TRUE, NOW(), NOW(), 'flyway', 'flyway'
    ),
    (
        'a1111111-1111-1111-1111-111111111113',
        'PT',
        '{"fr": "Portugal", "pt": "Portugal", "en": "Portugal"}'::jsonb,
        TRUE, NOW(), NOW(), 'flyway', 'flyway'
    );
