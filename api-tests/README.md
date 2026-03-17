# SaaS Tracker — API Tests

Blackbox HTTP tests for the SaaS Subscription Tracker backend.
Tests run against a live server and verify real HTTP responses — no mocks.

## Stack

| Tool | Purpose |
|---|---|
| TypeScript 5 | Language |
| Jest 29 | Test runner |
| axios 1 | HTTP client |
| @faker-js/faker 9 | Test data generation |
| dotenv 16 | `.env.test` loading |

## Project structure

```
api-tests/
├── package.json
├── tsconfig.json
├── jest.config.ts
├── .env.test.example          ← copy to .env.test and edit
└── src/
    ├── helpers/
    │   ├── client.ts           ← axios instance
    │   ├── auth.helper.ts      ← registerAndLogin(), authHeader(), cookie helpers
    │   └── fixtures.ts         ← subscriptionPayload() factory
    └── tests/
        ├── auth.test.ts
        ├── subscriptions.test.ts
        ├── subscriptions-filters.test.ts
        ├── subscriptions-export.test.ts
        ├── team.test.ts
        ├── analytics.test.ts
        ├── dashboard.test.ts
        ├── notifications.test.ts
        └── billing.test.ts
```

## Running locally

### Prerequisites

- Docker Desktop running
- The full `docker compose` stack started

### Steps

```bash
# 1. Start the application stack from the repo root
docker compose up -d

# 2. Move into the api-tests directory
cd api-tests

# 3. Copy and configure the env file
cp .env.test.example .env.test
# Edit .env.test if your stack runs on a different URL

# 4. Install dependencies
npm install

# 5. Run tests
npm test
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `BASE_URL` | `http://localhost:80` | Base URL of the running API server |

## Notes

### Cookie handling

The backend sets the `refresh_token` cookie with `Secure=true`. Because the
local test environment uses plain HTTP, axios will not send this cookie
automatically. The `auth.helper.ts` extracts the cookie from the `Set-Cookie`
response header and forwards it manually in subsequent requests that need it
(refresh, logout).

### Billing tests

`billing.test.ts` tests `GET /billing/status` unconditionally. The
`POST /billing/checkout` and `GET /billing/portal` tests allow both 200 and 502
responses — a 502 is expected when no Stripe API key is configured in the test
environment.

### Tenant isolation

Each test suite calls `registerAndLogin()` in `beforeAll`, which creates a new
company + admin user. This means suites are fully isolated and can run
concurrently without interfering with each other.

## CI

GitHub Actions runs these tests on every push and pull request to `main`.
See `.github/workflows/api-tests.yml`.
