UPDATE organizations
SET name_i18n = jsonb_build_object(
        'fr', 'Consulat de Guinée-Bissau en France',
        'pt', 'Consulado da Guiné-Bissau em França',
        'en', 'Guinea-Bissau Consulate in France'
    ),
    updated_at = NOW(),
    updated_by = 'flyway'
WHERE id = '11111111-1111-1111-1111-111111111111';

UPDATE organization_settings
SET settings = settings || jsonb_build_object(
        'activeLanguages', jsonb_build_array('fr', 'pt', 'en'),
        'addressI18n', jsonb_build_object(
            'fr', '45 rue de la Solidarité, 75019 Paris',
            'pt', '45 rue de la Solidarité, 75019 Paris',
            'en', '45 rue de la Solidarité, 75019 Paris'
        ),
        'openingHoursI18n', jsonb_build_object(
            'fr', 'Lun-Ven, 9h-16h',
            'pt', 'Seg-Sex, 9h-16h',
            'en', 'Mon-Fri, 9am-4pm'
        ),
        'contactEmail', 'contact@consulat-gw.example'
    ),
    updated_at = NOW(),
    updated_by = 'flyway'
WHERE organization_id = '11111111-1111-1111-1111-111111111111';
