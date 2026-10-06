INSERT INTO feature_flags (
    id, flag_key, organization_id, enabled, value_json, created_at, updated_at, created_by, updated_by
) VALUES (
    'd1111111-1111-1111-1111-11111111111b',
    'payment.provider.PAYDUNYA',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-11111111111c',
    'payment.provider.PAYDUNYA',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
);
