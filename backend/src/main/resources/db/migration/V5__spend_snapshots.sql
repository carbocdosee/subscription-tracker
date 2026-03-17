CREATE TABLE IF NOT EXISTS spend_snapshots (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    year SMALLINT NOT NULL,
    month SMALLINT NOT NULL,
    total_monthly_usd NUMERIC(14, 2) NOT NULL,
    subscription_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_spend_snapshot UNIQUE (company_id, year, month)
);

CREATE INDEX IF NOT EXISTS idx_spend_snapshots_company_year
    ON spend_snapshots(company_id, year DESC, month DESC);
