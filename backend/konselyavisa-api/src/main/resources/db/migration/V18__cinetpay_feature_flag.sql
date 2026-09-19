INSERT INTO feature_flags (
    id, flag_key, organization_id, enabled, value_json, created_at, updated_at, created_by, updated_by
) VALUES (
    'd1111111-1111-1111-1111-111111111119',
    'payment.provider.CINETPAY',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-11111111111a',
    'payment.provider.CINETPAY',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
);
