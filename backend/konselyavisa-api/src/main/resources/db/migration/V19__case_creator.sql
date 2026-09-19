ALTER TABLE cases
    ADD COLUMN created_by_role VARCHAR(40),
    ADD COLUMN created_by_label VARCHAR(120);
