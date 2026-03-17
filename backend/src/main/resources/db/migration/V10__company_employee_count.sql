ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS employee_count INTEGER;
-- NULL means "not set" — analytics falls back to counting active users
