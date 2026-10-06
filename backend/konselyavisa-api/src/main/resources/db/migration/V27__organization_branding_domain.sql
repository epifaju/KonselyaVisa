CREATE OR REPLACE FUNCTION app_resolve_org_id_by_domain(p_domain text)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT os.organization_id
    FROM organization_settings os
             INNER JOIN organizations o ON o.id = os.organization_id
    WHERE o.status = 'ACTIVE'
      AND NULLIF(btrim(p_domain), '') IS NOT NULL
      AND lower(btrim(os.settings ->> 'domain')) = lower(btrim(p_domain))
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION app_resolve_org_id_by_domain(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_resolve_org_id_by_domain(text) TO konselyavisa_app;

-- Demo org: PRD § white-label keys (brandColor + domain) for local / IT consumption.
UPDATE organization_settings
SET settings = settings || '{"brandColor": "#0B5D3B", "domain": "visa.demo.konselya.local"}'::jsonb,
    updated_at = NOW()
WHERE organization_id = '11111111-1111-1111-1111-111111111111';
