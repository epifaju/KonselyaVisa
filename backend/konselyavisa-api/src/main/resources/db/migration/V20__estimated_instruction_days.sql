ALTER TABLE procedure_versions
    ADD COLUMN estimated_instruction_days INTEGER;

ALTER TABLE procedure_versions
    ADD CONSTRAINT chk_procedure_versions_estimated_days
        CHECK (
            estimated_instruction_days IS NULL
            OR (estimated_instruction_days >= 1 AND estimated_instruction_days <= 365)
        );

UPDATE procedure_versions
SET estimated_instruction_days = 5
WHERE id = 'b1111111-1111-1111-1111-111111111112';

UPDATE procedure_versions
SET estimated_instruction_days = 10
WHERE id = 'b2222222-2222-2222-2222-222222222222';

UPDATE procedure_versions
SET estimated_instruction_days = 7
WHERE id = 'b3333333-3333-3333-3333-333333333332';
