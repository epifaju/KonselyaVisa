UPDATE procedure_definitions
SET name_i18n = '{
  "fr": "Apostille (cachet international)",
  "pt": "Apostila (selo internacional)",
  "en": "Apostille (international stamp)"
}'::jsonb,
    updated_at = NOW()
WHERE id = 'b3333333-3333-3333-3333-333333333331';
