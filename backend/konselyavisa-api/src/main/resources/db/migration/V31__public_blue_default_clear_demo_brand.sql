-- Public default theme is institutional blue (CSS 213 90% 52% / #1780F2).
-- DEMO keeps domain for Host resolution but no brandColor, so success-green stays free for statuses.
-- Orgs that want white-label set brandColor explicitly (prefer non-success hues).

UPDATE organization_settings
SET settings = (settings - 'brandColor') || '{"domain": "visa.demo.konselya.local"}'::jsonb,
    updated_at = NOW()
WHERE organization_id = '11111111-1111-1111-1111-111111111111';
