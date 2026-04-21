# Data Processing & Privacy Documentation

> **Audience:** Technical operators, Data Protection Officers, and auditors.
> This document supplements the in-app Privacy Policy (`/privacy`).

---

## 1. Sub-processors (Art. 28 GDPR)

| Sub-processor | Purpose | Location |
|---|---|---|
| PostgreSQL (self-hosted) | Primary data store | Operator's infrastructure |
| Redis (self-hosted) | Cache & rate-limiting | Operator's infrastructure |
| Resend / SMTP relay | Transactional email delivery | Configurable — see `EMAIL_PROVIDER` |
| Stripe | Payment processing & billing | USA (Standard Contractual Clauses apply) |

All sub-processors are bound by a Data Processing Agreement (DPA) with the operator, or via Stripe's
and Resend's published DPAs.

---

## 2. Data flows

```
User browser ──TLS──▶ Nginx ──▶ Ktor backend ──▶ PostgreSQL
                                              └──▶ Redis (cache, rate limits)
                                              └──▶ Email provider (invite, reset, renewal alerts)
                                              └──▶ Stripe (billing only)
```

No personal data is sent to third-party analytics, CDNs, or tracking services.
The renewal digest email HTML previously used `logo.clearbit.com` as a vendor logo fallback;
this call has been removed — logos are only loaded from URLs explicitly stored by users.

---

## 3. Categories of personal data processed

| Category | Field(s) | Storage location |
|---|---|---|
| Identity | `users.name`, `users.email` | PostgreSQL |
| Credentials | `users.password_hash` (BCrypt), `refresh_tokens.token_hash` (SHA-256), `password_reset_tokens.token_hash` (SHA-256) | PostgreSQL |
| Behavioural | `audit_log` (actions performed by user) | PostgreSQL |
| Transactional | `email_deliveries` (delivery status, recipient email) | PostgreSQL |
| Billing | `companies.stripe_customer_id` | PostgreSQL |

---

## 4. Retention schedule

| Data type | Retention period | Automated deletion |
|---|---|---|
| User account (PII) | While account is active | On account deletion (`DELETE /api/v1/user/account`) |
| Refresh tokens | 30 days from issuance | Daily `DataRetentionJob` |
| Password reset tokens | 1 hour from issuance | Daily `DataRetentionJob`; also purged per-user on new request |
| Email delivery logs | 90 days | Daily `DataRetentionJob` |
| Audit log entries | 2 years | Daily `DataRetentionJob` |
| Spend snapshots | Indefinitely (aggregated, no PII) | Not auto-deleted |

The `DataRetentionJob` runs daily at 03:00 UTC (configurable via `RENEWAL_CRON`).

---

## 5. Data subject rights — implementation

| Right | Art. | Implementation |
|---|---|---|
| Access / Portability | 15, 20 | `GET /api/v1/user/export` — returns JSON with profile, company, audit log |
| Erasure | 17 | `DELETE /api/v1/user/account` — deletes company + cascade if last member; otherwise anonymises user record |
| Rectification | 16 | Name / email editable in account settings |
| Object | 21 | Contact operator |
| Lodge a complaint | 77 | Contact the supervisory authority in your EEA member state |

---

## 6. Consent

Registration requires explicit GDPR consent (`gdprConsent: true` in `RegisterRequest`).
The consent checkbox links to the in-app Privacy Policy (`/privacy`) and Terms of Service (`/terms`).
Consent is validated server-side in `RequestValidators.kt`.

---

## 7. Security measures (Art. 32)

- Passwords: BCrypt (cost factor 12)
- Tokens: stored as SHA-256 hashes; raw token never persisted
- Transport: TLS enforced by Nginx; SMTP TLS enforced by `SmtpConfig.startTls = true`
- Access tokens: 15-minute TTL; automatic silent refresh via HttpOnly cookie
- Session inactivity timeout: 30 minutes (frontend, configurable in `InactivityService`)
- Redis: password-protected via `REDIS_PASSWORD`; URI includes credentials
- Log sanitization: `MaskingJsonGeneratorDecorator` masks email addresses, hex tokens, and JWTs in all log output
- Rate limiting: global + per-endpoint (auth: 30 rpm, export: 5 rpm)
- Role-based access control: `ADMIN`, `EDITOR`, `VIEWER`
- CORS: explicit allow-list via `CORS_ALLOWED_ORIGINS`

---

## 8. Data residency

All data resides within the operator's own infrastructure (Docker / PostgreSQL). No personal data
is transferred outside the operator's environment unless Stripe billing or an external email provider
is configured (see Section 1).
