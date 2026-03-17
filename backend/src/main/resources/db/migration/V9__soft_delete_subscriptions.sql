ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archived_by_id UUID REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_archived
    ON subscriptions(company_id) WHERE archived_at IS NULL;
