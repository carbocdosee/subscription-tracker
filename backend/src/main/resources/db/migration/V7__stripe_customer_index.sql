CREATE INDEX IF NOT EXISTS idx_companies_stripe_customer_id
    ON companies(stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;
