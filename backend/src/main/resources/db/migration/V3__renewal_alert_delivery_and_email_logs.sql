ALTER TABLE renewal_alerts
    ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS failure_reason TEXT;

ALTER TABLE renewal_alerts
    ALTER COLUMN sent_at DROP NOT NULL;

UPDATE renewal_alerts
SET delivery_status = 'SENT'
WHERE delivery_status IS NULL OR delivery_status = '';

CREATE TABLE IF NOT EXISTS email_deliveries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    invitation_id UUID REFERENCES team_invitations(id) ON DELETE CASCADE,
    recipient_email VARCHAR(255) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider_message_id VARCHAR(255),
    provider_status_code INTEGER,
    provider_response TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_deliveries_invitation_created
    ON email_deliveries(invitation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_deliveries_company_created
    ON email_deliveries(company_id, created_at DESC);
