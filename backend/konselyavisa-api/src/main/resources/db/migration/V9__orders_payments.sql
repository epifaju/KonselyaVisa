ALTER TABLE procedure_versions
    ADD COLUMN pricing JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE procedure_versions
    ADD CONSTRAINT chk_procedure_versions_pricing CHECK (jsonb_typeof(pricing) = 'object');

UPDATE procedure_versions
SET pricing = '{"currency": "EUR", "amountMinor": 8500}'::jsonb
WHERE id = 'b1111111-1111-1111-1111-111111111112';

UPDATE organization_settings
SET settings = settings || '{"paymentProvider": "MOCK"}'::jsonb
WHERE organization_id = '11111111-1111-1111-1111-111111111111';

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    case_id             UUID         NOT NULL REFERENCES cases (id),
    reference           VARCHAR(40)  NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    provider_code       VARCHAR(40)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uq_orders_org_reference UNIQUE (organization_id, reference),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PENDING_MANUAL', 'PAID', 'CANCELLED')),
    CONSTRAINT chk_orders_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_orders_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX uq_orders_case_open
    ON orders (case_id)
    WHERE status IN ('PENDING_PAYMENT', 'PENDING_MANUAL', 'PAID');

CREATE INDEX idx_orders_org_status ON orders (organization_id, status);

CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    organization_id       UUID         NOT NULL REFERENCES organizations (id),
    order_id              UUID         NOT NULL REFERENCES orders (id),
    provider_code         VARCHAR(40)  NOT NULL,
    provider_reference    VARCHAR(120),
    status                VARCHAR(20)  NOT NULL,
    currency              VARCHAR(3)   NOT NULL,
    amount_minor          BIGINT       NOT NULL,
    checkout_url          VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'PENDING_MANUAL', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_payments_amount CHECK (amount_minor > 0)
);

CREATE UNIQUE INDEX uq_payments_provider_reference
    ON payments (provider_code, provider_reference)
    WHERE provider_reference IS NOT NULL;

CREATE INDEX idx_payments_order ON payments (order_id);

CREATE TABLE payment_webhook_receipts (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    payment_id          UUID         REFERENCES payments (id),
    provider_code       VARCHAR(40)  NOT NULL,
    idempotency_key     VARCHAR(200) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uq_payment_webhook_idempotency UNIQUE (provider_code, idempotency_key)
);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

ALTER TABLE payment_webhook_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_webhook_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_webhook_receipts
    USING (app_is_platform_admin() OR organization_id = app_current_org())
    WITH CHECK (app_is_platform_admin() OR organization_id = app_current_org());

GRANT SELECT, INSERT, UPDATE, DELETE ON orders, payments, payment_webhook_receipts TO konselyavisa_app;
