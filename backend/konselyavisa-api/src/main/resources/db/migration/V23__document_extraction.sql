ALTER TABLE documents
    ADD COLUMN extracted_fields jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN document_validations jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN ai_confidence NUMERIC(5, 4),
    ADD COLUMN extraction_source VARCHAR(20);

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_extraction_source
        CHECK (extraction_source IS NULL OR extraction_source IN ('MANUAL', 'OCR')),
    ADD CONSTRAINT chk_documents_ai_confidence
        CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1));
