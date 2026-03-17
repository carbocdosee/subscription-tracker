CREATE TABLE IF NOT EXISTS user_notification_reads (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_key VARCHAR(255) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, notification_key)
);

CREATE INDEX IF NOT EXISTS idx_user_notification_reads_user_read_at
    ON user_notification_reads(user_id, read_at DESC);
