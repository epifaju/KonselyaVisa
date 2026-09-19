INSERT INTO feature_flags (
    id, flag_key, organization_id, enabled, value_json, created_at, updated_at, created_by, updated_by
) VALUES (
    'd1111111-1111-1111-1111-111111111115',
    'payment.provider.MOCK',
    NULL,
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-111111111116',
    'payment.provider.MANUAL',
    NULL,
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-111111111117',
    'payment.provider.STRIPE',
    NULL,
    FALSE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
), (
    'd1111111-1111-1111-1111-111111111118',
    'payment.provider.STRIPE',
    '11111111-1111-1111-1111-111111111111',
    TRUE,
    '{}'::jsonb,
    NOW(), NOW(), 'flyway', 'flyway'
);
