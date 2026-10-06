ALTER TABLE privacy_consents DROP CONSTRAINT chk_privacy_consents_purpose;
ALTER TABLE privacy_consents
    ADD CONSTRAINT chk_privacy_consents_purpose CHECK (purpose IN ('CASE_DEPOSIT', 'ACCOUNT_CREATION'));
