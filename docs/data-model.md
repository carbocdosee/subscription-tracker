# Data Model

## ER Overview

```text
companies (1) --- (N) users
companies (1) --- (N) subscriptions
companies (1) --- (N) renewal_alerts
companies (1) --- (N) audit_log
companies (1) --- (N) team_invitations
subscriptions (1) --- (N) renewal_alerts
subscriptions (1) --- (N) subscription_comments
subscriptions (1) --- (N) subscription_payments
users (1) --- (N) subscriptions.created_by_id
users (1) --- (N) subscriptions.owner_id
users (1) --- (N) subscription_comments.user_id
users (1) --- (N) subscription_payments.recorded_by_user_id
```

## Tables

### `companies`
- Stores tenant/company profile.
- Key fields:
  - `domain` unique tenant identifier.
  - `subscription_status` billing access state.
  - `trial_ends_at` trial enforcement boundary.
  - `monthly_budget` used for budget vs actual analytics.

### `users`
- All users inside a company tenant.
- `role` controls RBAC (`ADMIN`, `EDITOR`, `VIEWER`).
- `is_active` allows soft deactivation without deleting audit history.

### `subscriptions`
- Core SaaS contracts/payments tracked by company.
- `billing_cycle` + `amount` normalized for dashboard KPIs.
- `renewal_date` drives renewal alerts and forecasting.
- `payment_mode` supports `AUTO` and `MANUAL` workflows.
- `payment_status` + `next_payment_date` track manual payment operations.
- `tags` stored as JSON array string for fast write/read portability.

### `renewal_alerts`
- Idempotent alert ledger.
- Uniqueness:
  - `subscription_id + alert_window_days + renewal_date_snapshot`
  - ensures a threshold alert is sent once per renewal cycle.

### `audit_log`
- Immutable mutation history:
  - action
  - entity type
  - previous/new value snapshots.

### `team_invitations`
- Invitation workflow records.
- Stores token, target role, expiry, acceptance state.

### `subscription_comments`
- Team collaboration thread per subscription.

### `subscription_payments`
- Immutable ledger for manual payment actions (`mark as paid`).
- Captures who recorded payment, amount/currency, paid date and optional reference/note.

## Relationship Semantics
- Deleting a company cascades to users, subscriptions, invitations, comments.
- Deleting a subscription cascades to `renewal_alerts`, comments and payment history.
- Audit log rows are preserved until explicit retention policy cleanup.
