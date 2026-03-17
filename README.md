# Subscription Tracker MVP

Production-oriented MVP for small businesses to track company SaaS subscriptions, renewal alerts, spend analytics, duplicate tooling, and team collaboration.

## Stack
- Backend: Kotlin 2.0 + Ktor 2.x + Koin + Exposed + Flyway + PostgreSQL + Redis + Quartz + JWT + Valiktor
- Frontend: Angular 17 standalone + PrimeNG + PrimeFlex + NgRx Signals
- Infra: Docker Compose + Nginx + GitHub Actions

## Quick Start (5 commands)
```bash
cp .env.example .env
docker compose build
docker compose up -d
docker compose ps
curl http://localhost/health
```

## URLs
- App gateway: `http://localhost`
- Backend direct: `http://localhost:8080`
- Frontend direct: `http://localhost:4200`
- Health: `http://localhost/health`
- Metrics: `http://localhost/metrics`
- Stalwart admin UI: `http://localhost:8081` *(local dev only)*

## Email Setup

### Local Development (Stalwart — built-in SMTP)
For local development, docker-compose already includes Stalwart — a self-hosted SMTP server.
1. `docker compose up -d`
2. Read generated admin password: `docker compose logs stalwart --tail=20`
3. Update `.env` so `SMTP_PASSWORD` matches the generated `admin` password.
4. Open Stalwart admin UI at `http://localhost:8081`, create a mailbox, and use the same address in `SMTP_FROM_EMAIL`.
5. Keep `EMAIL_PROVIDER=smtp` and restart backend: `docker compose up -d --force-recreate backend`

> ⚠️ **Local development only.** In production, Stalwart without DKIM/SPF/DMARC records will cause
> all outgoing emails (renewal alerts, invitations) to land in spam.

### Production (Resend — recommended)
Resend is a transactional email service with a free tier (3,000 emails/month).
1. Sign up at [resend.com](https://resend.com)
2. Add and verify your domain (DKIM is configured automatically)
3. Create an API key
4. In `.env`: set `EMAIL_PROVIDER=resend`, `RESEND_API_KEY=re_...`, `RESEND_FROM_EMAIL=noreply@yourdomain.com`
5. Stalwart is not needed in production — you can remove it from docker-compose or keep it for local dev only.

### Production (SMTP provider — alternative)
Works with any SMTP provider: SendGrid, Postmark, Mailgun, AWS SES.
In `.env`: set `EMAIL_PROVIDER=smtp` and fill in the `SMTP_*` variables.
Ensure your sender domain has DKIM and SPF records configured.

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `APP_ENV` | no | runtime environment label |
| `APP_HOST` | no | bind host |
| `APP_PORT` | no | backend port |
| `DATABASE_URL` | yes | JDBC URL for PostgreSQL |
| `DATABASE_USER` | yes | DB user |
| `DATABASE_PASSWORD` | yes | DB password |
| `DATABASE_MAX_POOL_SIZE` | no | Hikari max pool size |
| `REDIS_URL` | yes | Redis connection URI |
| `REDIS_CACHE_TTL_SECONDS` | no | cache TTL default |
| `JWT_ISSUER` | yes | token issuer |
| `JWT_AUDIENCE` | yes | token audience |
| `JWT_SECRET` | yes | HMAC secret for JWT |
| `JWT_ACCESS_TTL_MINUTES` | no | token TTL |
| `EMAIL_PROVIDER` | no | email backend: `smtp` (default) or `resend` |
| `SMTP_HOST` | yes (smtp) | SMTP host (`stalwart` in compose network) |
| `SMTP_PORT` | yes (smtp) | SMTP port (usually `587`) |
| `SMTP_USERNAME` | yes (smtp auth) | SMTP login |
| `SMTP_PASSWORD` | yes (smtp auth) | SMTP password |
| `SMTP_FROM_EMAIL` | yes (smtp) | sender email address |
| `SMTP_FROM_NAME` | no | sender display name |
| `SMTP_AUTH` | no | enable SMTP auth (`true`/`false`) |
| `SMTP_STARTTLS` | no | enable STARTTLS (`true`/`false`) |
| `SMTP_TLS_TRUST_ALL` | no | trust all SMTP TLS certs (dev only) |
| `SMTP_TIMEOUT_MS` | no | SMTP connect/read/write timeout |
| `RESEND_API_KEY` | no | Resend API key (used only when `EMAIL_PROVIDER=resend`) |
| `RESEND_FROM_EMAIL` | no | sender email for Resend mode |
| `RESEND_BASE_URL` | no | Resend API base |
| `FRONTEND_BASE_URL` | no | base URL used in invite emails (`/accept-invite?token=...`) |
| `STRIPE_SECRET_KEY` | no | Stripe private key |
| `STRIPE_WEBHOOK_SECRET` | no | Stripe webhook secret |
| `STRIPE_MONTHLY_PRICE_ID` | no | Stripe pricing id |
| `RENEWAL_CRON` | no | Quartz cron for renewal check |
| `RATE_LIMIT_RPM` | no | global request limit per IP per minute |
| `CORS_ALLOWED_ORIGINS` | yes | comma-separated allowed origins |
| `DEFAULT_EMPLOYEE_COUNT` | no | fallback employee count in analytics |
| `POSTGRES_DB` | yes | compose database name |
| `POSTGRES_USER` | yes | compose database user |
| `POSTGRES_PASSWORD` | yes | compose database password |
| `STALWART_HTTP_PORT` | no | host port for Stalwart admin UI |
| `STALWART_SUBMISSION_PORT` | no | host port mapped to Stalwart submission `587` |

## API Documentation
- OpenAPI: `docs/api/openapi.yaml`
- BPMN:
  - `docs/bpmn/subscription_renewal_alert.bpmn`
  - `docs/bpmn/subscription_management.bpmn`
  - `docs/bpmn/team_onboarding.bpmn`
- Data model: `docs/data-model.md`
- User guide: `docs/user-guide.md`
- Email templates: `docs/email-templates.md`

## Key Features Implemented
- Auth:
  - Register/login
  - JWT access tokens
  - Team invite and acceptance flow
- Subscriptions:
  - CRUD with validation
  - automatic/manual payment mode
  - `mark as paid` action for manual payments (with next payment recalculation)
  - CSV import
  - duplicate detection (vendor/category)
  - vendor logo resolution (Clearbit URL convention)
  - ownership + comments
- Dashboard:
  - monthly and annual spend
  - renewals in next 30 days
  - category breakdown and trend data
  - potential savings from duplicates
- Analytics:
  - YoY comparison
  - growing subscriptions
  - budget utilization
  - unused detector (usage signal hook)
  - cost per employee
- Alerts:
  - Quartz daily check for 90/60/30/7/1 day windows
  - idempotent alert ledger
  - SMTP digest emails (Stalwart for local dev, Resend recommended for production)
  - in-app notification center (header bell) with unread count and mark-as-read actions
- Security & operations:
  - request validation
  - role-based access
  - rate limiting
  - CORS configuration
  - structured JSON logging with request id
  - `/health` dependency checks
  - Prometheus metrics endpoint

## Testing
- Unit tests:
  - `backend/src/test/kotlin/com/saastracker/domain/service/SubscriptionServiceTest.kt`
  - `backend/src/test/kotlin/com/saastracker/domain/service/AlertServiceTest.kt`
- Integration test:
  - `backend/src/integrationTest/kotlin/com/saastracker/integration/SubscriptionIntegrationTest.kt`
- Frontend:
  - Jest + Testing Library spec
  - Cypress E2E scenario suite
- CI:
  - `.github/workflows/ci.yml`

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

## Notes
- Stripe enforcement is wired in backend service and webhook route.
- Email sending logs a warning and skips delivery when required SMTP settings are absent.
- Team invite endpoint returns `acceptInviteUrl` so admins can share the link manually when email delivery is disabled.
- Team invite response also includes `emailDelivery.status` (`SENT`, `SKIPPED_NOT_CONFIGURED`, `FAILED`) and an optional provider message.
