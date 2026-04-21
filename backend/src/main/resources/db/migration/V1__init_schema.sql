-- -----------------------------------------------------------------------------
-- companies
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companies (
    id                     UUID          PRIMARY KEY,
    name                   VARCHAR(255)  NOT NULL,
    domain                 VARCHAR(255)  NOT NULL UNIQUE,
    stripe_customer_id     VARCHAR(255),
    subscription_status    VARCHAR(50)   NOT NULL DEFAULT 'TRIAL',
    trial_ends_at          TIMESTAMPTZ   NOT NULL DEFAULT (NOW() + INTERVAL '14 days'),
    monthly_budget         NUMERIC(10,2),
    employee_count         INTEGER,
    settings               JSONB         NOT NULL DEFAULT '{}'::JSONB,
    plan_tier              VARCHAR(20)   NOT NULL DEFAULT 'FREE',
    weekly_digest_enabled  BOOLEAN       NOT NULL DEFAULT TRUE,
    timezone               VARCHAR(64)   NOT NULL DEFAULT 'UTC',
    zombie_threshold_days  INTEGER       NOT NULL DEFAULT 60,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_companies_plan_tier
    ON companies(plan_tier);

CREATE INDEX IF NOT EXISTS idx_companies_stripe_customer_id
    ON companies(stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            UUID          PRIMARY KEY,
    company_id    UUID          NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    name          VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    role          VARCHAR(50)   NOT NULL DEFAULT 'VIEWER',
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- subscriptions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscriptions (
    id                UUID          PRIMARY KEY,
    company_id        UUID          NOT NULL REFERENCES companies(id)   ON DELETE CASCADE,
    created_by_id     UUID          NOT NULL REFERENCES users(id),
    vendor_name       VARCHAR(255)  NOT NULL,
    vendor_url        VARCHAR(255),
    vendor_logo_url   VARCHAR(500),
    category          VARCHAR(100)  NOT NULL,
    description       TEXT,
    amount            NUMERIC(10,2) NOT NULL,
    currency          CHAR(3)       NOT NULL DEFAULT 'USD',
    billing_cycle     VARCHAR(20)   NOT NULL,
    renewal_date      DATE          NOT NULL,
    contract_start_date DATE,
    auto_renews       BOOLEAN       NOT NULL DEFAULT TRUE,
    payment_mode      VARCHAR(20)   NOT NULL DEFAULT 'AUTO',
    payment_status    VARCHAR(20)   NOT NULL DEFAULT 'PAID',
    last_paid_at      DATE,
    next_payment_date DATE,
    status            VARCHAR(50)   NOT NULL DEFAULT 'ACTIVE',
    tags              TEXT,
    owner_id          UUID          REFERENCES users(id),
    notes             TEXT,
    document_url      VARCHAR(500),
    archived_at       TIMESTAMPTZ,
    archived_by_id    UUID          REFERENCES users(id),
    last_used_at      TIMESTAMPTZ,
    is_zombie         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_company
    ON subscriptions(company_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_renewal_date
    ON subscriptions(renewal_date);
CREATE INDEX IF NOT EXISTS idx_subscriptions_vendor_company
    ON subscriptions(company_id, vendor_name);
CREATE INDEX IF NOT EXISTS idx_subscriptions_category_company
    ON subscriptions(company_id, category);
CREATE INDEX IF NOT EXISTS idx_subscriptions_archived
    ON subscriptions(company_id) WHERE archived_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_subscriptions_is_zombie
    ON subscriptions(company_id, is_zombie)
    WHERE is_zombie = TRUE AND archived_at IS NULL;

-- -----------------------------------------------------------------------------
-- renewal_alerts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS renewal_alerts (
    id                   UUID        PRIMARY KEY,
    subscription_id      UUID        NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    company_id           UUID        NOT NULL REFERENCES companies(id)     ON DELETE CASCADE,
    alert_type           VARCHAR(20) NOT NULL,
    alert_window_days    SMALLINT    NOT NULL,
    renewal_date_snapshot DATE       NOT NULL,
    delivery_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at              TIMESTAMPTZ,
    failure_reason       TEXT,
    email_recipients     TEXT        NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_renewal_alert_uniqueness
    ON renewal_alerts(subscription_id, alert_window_days, renewal_date_snapshot);

-- -----------------------------------------------------------------------------
-- team_invitations
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_invitations (
    id                  UUID         PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES companies(id)  ON DELETE CASCADE,
    invited_by_user_id  UUID         NOT NULL REFERENCES users(id),
    email               VARCHAR(255) NOT NULL,
    role                VARCHAR(50)  NOT NULL,
    token               VARCHAR(255) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ  NOT NULL,
    accepted_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(company_id, email)
);

-- -----------------------------------------------------------------------------
-- email_deliveries (references team_invitations, so must come after)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_deliveries (
    id                    UUID         PRIMARY KEY,
    company_id            UUID         NOT NULL REFERENCES companies(id)       ON DELETE CASCADE,
    invitation_id         UUID         REFERENCES team_invitations(id)         ON DELETE CASCADE,
    recipient_email       VARCHAR(255) NOT NULL,
    template_type         VARCHAR(50)  NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    provider_message_id   VARCHAR(255),
    provider_status_code  INTEGER,
    provider_response     TEXT,
    error_message         TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_deliveries_invitation_created
    ON email_deliveries(invitation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_deliveries_company_created
    ON email_deliveries(company_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- subscription_comments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscription_comments (
    id              UUID        PRIMARY KEY,
    subscription_id UUID        NOT NULL REFERENCES subscriptions(id)   ON DELETE CASCADE,
    company_id      UUID        NOT NULL REFERENCES companies(id)        ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id),
    body            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscription_comments_subscription
    ON subscription_comments(subscription_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- subscription_payments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscription_payments (
    id                    UUID          PRIMARY KEY,
    subscription_id       UUID          NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    company_id            UUID          NOT NULL REFERENCES companies(id)      ON DELETE CASCADE,
    recorded_by_user_id   UUID          NOT NULL REFERENCES users(id),
    amount                NUMERIC(10,2) NOT NULL,
    currency              CHAR(3)       NOT NULL DEFAULT 'USD',
    paid_at               DATE          NOT NULL,
    payment_reference     VARCHAR(255),
    note                  TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscription_payments_subscription
    ON subscription_payments(subscription_id, paid_at DESC);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_company
    ON subscription_payments(company_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- user_notification_reads
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_notification_reads (
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_key  VARCHAR(255) NOT NULL,
    read_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, notification_key)
);

CREATE INDEX IF NOT EXISTS idx_user_notification_reads_user_read_at
    ON user_notification_reads(user_id, read_at DESC);

-- -----------------------------------------------------------------------------
-- spend_snapshots
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS spend_snapshots (
    id                  UUID          PRIMARY KEY,
    company_id          UUID          NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    year                SMALLINT      NOT NULL,
    month               SMALLINT      NOT NULL,
    total_monthly_usd   NUMERIC(14,2) NOT NULL,
    subscription_count  INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_spend_snapshot UNIQUE (company_id, year, month)
);

CREATE INDEX IF NOT EXISTS idx_spend_snapshots_company_year
    ON spend_snapshots(company_id, year DESC, month DESC);

-- -----------------------------------------------------------------------------
-- budget_alert_log
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS budget_alert_log (
    id                UUID     PRIMARY KEY,
    company_id        UUID     NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    year              SMALLINT NOT NULL,
    month             SMALLINT NOT NULL,
    threshold_percent SMALLINT NOT NULL,
    sent_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_budget_alert UNIQUE (company_id, year, month, threshold_percent)
);

-- -----------------------------------------------------------------------------
-- refresh_tokens
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- -----------------------------------------------------------------------------
-- password_reset_tokens
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prt_user_id ON password_reset_tokens(user_id);

-- -----------------------------------------------------------------------------
-- audit_log (company_id and user_id with ON DELETE CASCADE — from V13 fix)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID         PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   UUID         NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_company_created
    ON audit_log(company_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- savings_events (new — ROI counter)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS savings_events (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID          NOT NULL REFERENCES companies(id)     ON DELETE CASCADE,
    subscription_id UUID          REFERENCES subscriptions(id)          ON DELETE SET NULL,
    event_type      VARCHAR(50)   NOT NULL,
    vendor_name     VARCHAR(255)  NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        CHAR(3)       NOT NULL,
    saved_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_savings_events_company
    ON savings_events(company_id, saved_at DESC);
