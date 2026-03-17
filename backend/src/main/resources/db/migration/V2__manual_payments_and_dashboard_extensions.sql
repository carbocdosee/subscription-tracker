ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS payment_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'PAID',
    ADD COLUMN IF NOT EXISTS last_paid_at DATE,
    ADD COLUMN IF NOT EXISTS next_payment_date DATE;

CREATE TABLE IF NOT EXISTS subscription_payments (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    recorded_by_user_id UUID NOT NULL REFERENCES users(id),
    amount NUMERIC(10, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    paid_at DATE NOT NULL,
    payment_reference VARCHAR(255),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscription_payments_subscription
    ON subscription_payments(subscription_id, paid_at DESC);

CREATE INDEX IF NOT EXISTS idx_subscription_payments_company
    ON subscription_payments(company_id, created_at DESC);
