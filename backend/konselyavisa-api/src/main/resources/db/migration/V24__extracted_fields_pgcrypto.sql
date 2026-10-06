ALTER TABLE documents
    ADD COLUMN extracted_fields_cipher bytea;

UPDATE documents
SET extracted_fields_cipher = pgp_sym_encrypt(
        CAST(extracted_fields AS text),
        '${extractedFieldsKey}',
        'compress-algo=1, cipher-algo=aes256')
WHERE extracted_fields IS NOT NULL
  AND extracted_fields <> '{}'::jsonb;

ALTER TABLE documents
    DROP COLUMN extracted_fields;
