-- Migrate all existing TRIAL companies to ACTIVE.
-- TRIAL was a legacy status used before the freemium model (FREE/PRO/ENTERPRISE plan tiers).
-- Access control is now governed by plan_tier, not subscription_status.
UPDATE companies
SET subscription_status = 'ACTIVE'
WHERE subscription_status = 'TRIAL';

ALTER TABLE companies
    ALTER COLUMN subscription_status SET DEFAULT 'ACTIVE';
