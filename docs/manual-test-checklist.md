# Manual Test Checklist — SaaS Subscription Tracker

> Generated: 2026-03-12
> Tech stack: Kotlin/Ktor backend · Angular 17 / PrimeNG frontend
> Scope: All implemented features through task GROWTH-02

---

## How to use this checklist

| Symbol | Meaning |
|--------|---------|
| `[ ]`  | Not yet tested |
| `[P]`  | Pass |
| `[F]`  | Fail — log bug |
| `[S]`  | Skipped — note reason |

Mark each item before moving to the next section. File a bug for every `[F]` with: steps to reproduce, expected result, actual result, environment.

---

## 1. Authentication & Session Management

### 1.1 Registration

- [ ] **REG-01** Register with valid email, strong password, and company name → lands on dashboard, company is created
- [ ] **REG-02** Register with already-used email → inline error "email already in use"
- [ ] **REG-03** Register with password shorter than minimum (< 8 chars) → validation error shown before submission
- [ ] **REG-04** Register with blank required fields → form prevents submission, highlights missing fields
- [ ] **REG-05** Register with invalid email format → validation error shown

### 1.2 Login

- [ ] **LOG-01** Login with valid credentials → redirected to dashboard, access token set in memory, httpOnly refresh cookie set
- [ ] **LOG-02** Login with wrong password → "Invalid credentials" error, no token stored
- [ ] **LOG-03** Login with non-existent email → "Invalid credentials" error (no user enumeration)
- [ ] **LOG-04** Login with blank fields → form prevents submission
- [ ] **LOG-05** Rapid repeated failed logins (> 5 in quick succession) → rate limiting kicks in (429 or locked message)

### 1.3 Token Refresh & Session Persistence

- [ ] **TKN-01** After 15 minutes of idle session (access token expires), make any authenticated action → silent refresh using httpOnly cookie succeeds without visible interruption
- [ ] **TKN-02** Open app in new browser tab (same browser) → session restored correctly via refresh cookie
- [ ] **TKN-03** Clear browser cookies → next request redirects to login page
- [ ] **TKN-04** Logout → httpOnly cookie cleared, access token cleared from memory, redirect to login
- [ ] **TKN-05** After logout, pressing Back button and navigating to a protected route → redirected back to login

### 1.4 Team Invitation Flow

- [ ] **INV-01** Admin sends invitation to a new email → invitation email received, link contains valid token
- [ ] **INV-02** Click invitation link → `/accept-invite` page loaded, form pre-filled with invited email
- [ ] **INV-03** Accept invite with valid password → account created, user added to company, redirected to dashboard
- [ ] **INV-04** Accept invite with mismatched / weak password → validation error shown
- [ ] **INV-05** Reuse an already-accepted invitation link → "invitation invalid or expired" error
- [ ] **INV-06** Non-admin user attempts to invite team members → invite button absent or returns 403

---

## 2. Subscription Management

### 2.1 Create Subscription

- [ ] **SUB-C01** Create subscription with all required fields (vendor, amount, billing cycle, renewal date) → appears in list
- [ ] **SUB-C02** Create subscription using a predefined category from autocomplete → category saved and displayed correctly
- [ ] **SUB-C03** Create subscription with a custom/typed category → custom category saved, appears in category filter for future subscriptions
- [ ] **SUB-C04** Create subscription with a vendor that has a logo → vendor logo displayed in the list
- [ ] **SUB-C05** Create subscription without optional fields (notes, payment mode) → saved without error
- [ ] **SUB-C06** Submit create form with blank required fields → form prevents submission, highlights missing fields
- [ ] **SUB-C07** Enter a negative amount → validation error shown

### 2.2 Edit Subscription

- [ ] **SUB-E01** Edit an existing subscription (change vendor name, amount, renewal date) → changes saved and reflected in list
- [ ] **SUB-E02** Change billing cycle (e.g., Monthly → Annual) → cost calculations on dashboard update accordingly
- [ ] **SUB-E03** Edit subscription as Viewer role → edit controls not visible / return 403
- [ ] **SUB-E04** Edit subscription as Editor role → edit allowed

### 2.3 Archive (Soft Delete) Subscription

- [ ] **SUB-D01** Click Delete on a subscription → PrimeNG ConfirmDialog appears asking for confirmation
- [ ] **SUB-D02** Confirm deletion → subscription removed from active list, `archived_at` timestamp set
- [ ] **SUB-D03** Cancel deletion → subscription remains in active list unchanged
- [ ] **SUB-D04** Admin visits `/subscriptions/archived` (or archived view) → archived subscriptions listed with archive date
- [ ] **SUB-D05** Archived subscriptions do NOT appear in the default active subscriptions list
- [ ] **SUB-D06** Archived subscriptions do NOT appear in dashboard spend totals
- [ ] **SUB-D07** Viewer role cannot archive subscriptions

### 2.4 Filtering & Pagination

- [ ] **FIL-01** Filter by vendor name (partial match, case-insensitive) → only matching subscriptions shown
- [ ] **FIL-02** Filter by category → only subscriptions in that category shown
- [ ] **FIL-03** Filter by status (Active / Cancelled / Trial) → correct subset shown
- [ ] **FIL-04** Filter by payment mode (Credit Card / Invoice / etc.) → correct subset shown
- [ ] **FIL-05** Filter by minimum amount → subscriptions below threshold excluded
- [ ] **FIL-06** Filter by maximum amount → subscriptions above threshold excluded
- [ ] **FIL-07** Filter by amount range (min + max combined) → only subscriptions within range shown
- [ ] **FIL-08** Apply multiple filters simultaneously → result is intersection of all filter criteria
- [ ] **FIL-09** Clear all filters → full active subscription list restored
- [ ] **FIL-10** Navigate to page 2 of results → correct second page of subscriptions shown
- [ ] **FIL-11** Change page size → list updates to show the new number of items per page
- [ ] **FIL-12** Sort by amount ascending → cheapest subscriptions first
- [ ] **FIL-13** Sort by renewal date ascending → soonest renewals first
- [ ] **FIL-14** Apply filter that returns zero results → empty state message shown, no JS errors
- [ ] **FIL-15** Filter by vendor, then paginate → pagination reflects filtered count, not total count

### 2.5 Categories Autocomplete

- [ ] **CAT-01** Type in category field → predefined categories + existing custom categories appear in autocomplete
- [ ] **CAT-02** Select a predefined category → saved correctly
- [ ] **CAT-03** Type a brand new category name → accepted and saved as a custom category
- [ ] **CAT-04** GET `/api/v1/subscriptions/categories` response contains both `predefined` and `custom` arrays

### 2.6 Export

- [ ] **EXP-01** Export active list as CSV (no filters) → file downloaded, contains correct header row and all active subscriptions
- [ ] **EXP-02** Export as CSV with filters applied → only filtered subscriptions appear in the exported file
- [ ] **EXP-03** Export as PDF (no filters) → PDF downloaded, table is readable with headers and rows
- [ ] **EXP-04** Export as PDF with filters applied → only filtered subscriptions appear in the PDF
- [ ] **EXP-05** Export when list is empty (no matches for current filter) → file downloads without errors (empty table / header only)
- [ ] **EXP-06** Export button shows loading/spinner state while download is in progress
- [ ] **EXP-07** PDF contains company name in header/title area
- [ ] **EXP-08** CSV values with commas or quotes in them (e.g., vendor "Acme, Inc.") are correctly escaped

---

## 3. Dashboard

- [ ] **DSH-01** Dashboard loads without errors after login
- [ ] **DSH-02** Total monthly spend matches the sum of active subscriptions' monthly-normalized amounts
- [ ] **DSH-03** Upcoming renewals widget lists subscriptions renewing within the next 30 days in date order
- [ ] **DSH-04** Spend by category chart renders with correct slices/bars matching subscription categories
- [ ] **DSH-05** After adding a new subscription, dashboard totals update on refresh
- [ ] **DSH-06** After archiving a subscription, dashboard totals decrease accordingly
- [ ] **DSH-07** Dashboard with zero subscriptions → shows empty/zero state cleanly (no chart errors, no NaN)

---

## 4. Analytics

- [ ] **ANA-01** Analytics page loads without JS errors
- [ ] **ANA-02** Year-over-year spend comparison chart is displayed (current year vs prior year)
- [ ] **ANA-03** YoY chart correctly reflects spend snapshot data (not real-time subscription list)
- [ ] **ANA-04** Cost per employee metric is calculated as `total monthly spend / employee count`
- [ ] **ANA-05** Cost per employee falls back correctly when employee count is not set (uses active user count)
- [ ] **ANA-06** After admin sets employee count via team page, cost per employee on analytics page updates
- [ ] **ANA-07** Health score / subscription health metric is present and within a logical range (0–100 or A–F)
- [ ] **ANA-08** Admin POST `/api/v1/admin/snapshots/capture` creates a spend snapshot for the current month
- [ ] **ANA-09** Analytics page shows meaningful data after at least two monthly snapshots exist

---

## 5. Team Management

- [ ] **TEAM-01** Team page lists all team members with name, email, and role
- [ ] **TEAM-02** Admin can see and use the "Invite Member" button
- [ ] **TEAM-03** Non-admin cannot see Invite Member button
- [ ] **TEAM-04** Admin can change a member's role (e.g., Viewer → Editor)
- [ ] **TEAM-05** Admin can remove a team member
- [ ] **TEAM-06** Employee count field is visible on the team page
- [ ] **TEAM-07** Admin updates employee count → saved successfully, analytics reflect new value
- [ ] **TEAM-08** Non-admin cannot edit employee count (field disabled or request returns 403)
- [ ] **TEAM-09** PATCH `/api/v1/company` with employee count = 0 or negative → validation error
- [ ] **TEAM-10** Pending invitations are shown in the team list with "Pending" status

---

## 6. Notifications

- [ ] **NOT-01** Notification bell icon shows unread count badge when there are unread notifications
- [ ] **NOT-02** Opening notification panel lists all notifications in reverse-chronological order
- [ ] **NOT-03** Clicking a notification (or "Mark as read") clears the unread badge for that item
- [ ] **NOT-04** "Mark all as read" clears all badges and unread count goes to 0
- [ ] **NOT-05** After renewal check job runs, a renewal alert notification appears for subscriptions renewing within alert threshold
- [ ] **NOT-06** Budget alert notification appears when total spend exceeds the configured threshold
- [ ] **NOT-07** Notification count badge disappears when all notifications are read

---

## 7. Renewal Alerts & Budget Alerts

- [ ] **ALRT-01** Subscription with renewal date within 7 days → renewal alert created by RenewalCheckJob
- [ ] **ALRT-02** Subscription with renewal date within 30 days → renewal alert created (if 30-day threshold configured)
- [ ] **ALRT-03** Renewal alert email is delivered to the user (check inbox/SMTP logs)
- [ ] **ALRT-04** Budget alert fires when monthly spend crosses the configured threshold
- [ ] **ALRT-05** Budget alert is logged in `budget_alert_log` table
- [ ] **ALRT-06** Budget alert email is sent to admin users
- [ ] **ALRT-07** No duplicate alerts are created for the same subscription/period
- [ ] **ALRT-08** Archived subscriptions do NOT trigger renewal alerts

---

## 8. Billing & Stripe Integration

- [ ] **BIL-01** Upgrade page loads and shows plan options
- [ ] **BIL-02** Clicking "Upgrade" redirects to Stripe Checkout (or opens Stripe iframe)
- [ ] **BIL-03** Successful Stripe payment → company status updated to paid plan in DB (`stripe_customer_id` stored)
- [ ] **BIL-04** Stripe webhook `customer.subscription.updated` → company plan status updated correctly
- [ ] **BIL-05** Stripe webhook `customer.subscription.deleted` → company plan downgraded/cancelled
- [ ] **BIL-06** Company can be found by `stripe_customer_id` (index in V7 migration functions correctly)
- [ ] **BIL-07** Invalid/unsigned Stripe webhook → returns 400, not processed

---

## 9. Role-Based Access Control

- [ ] **RBAC-01** Admin can perform all CRUD operations on subscriptions
- [ ] **RBAC-02** Editor can create and edit subscriptions but cannot access team management routes
- [ ] **RBAC-03** Viewer can only read data; create/edit/delete controls are hidden or return 403
- [ ] **RBAC-04** GET `/api/v1/subscriptions/archived` returns 403 for non-admin users
- [ ] **RBAC-05** POST `/api/v1/admin/snapshots/capture` returns 403 for non-admin users
- [ ] **RBAC-06** PATCH `/api/v1/company` returns 403 for non-admin users
- [ ] **RBAC-07** Users from Company A cannot read or modify subscriptions belonging to Company B (multi-tenant isolation)

---

## 10. Error Handling & Edge Cases

- [ ] **ERR-01** Backend is unreachable → frontend shows a user-friendly error message, not a blank page or console-only error
- [ ] **ERR-02** JWT token is tampered with → returns 401, user redirected to login
- [ ] **ERR-03** Request body is malformed JSON → backend returns 400 with descriptive error message
- [ ] **ERR-04** Creating a subscription with an amount exceeding max integer/decimal range → graceful error
- [ ] **ERR-05** Accessing a route that doesn't exist → 404 returned, not a 500
- [ ] **ERR-06** Concurrent edits to the same subscription from two browser tabs → no data corruption
- [ ] **ERR-07** Very long string in vendor name field (> 255 chars) → validation error, not a DB truncation error

---

## 11. Performance & Reliability (Smoke)

- [ ] **PERF-01** Dashboard page fully loads in under 3 seconds with < 50 subscriptions
- [ ] **PERF-02** Subscriptions list with 200+ records paginates and filters without perceptible lag
- [ ] **PERF-03** CSV export of 500 subscriptions completes in under 10 seconds
- [ ] **PERF-04** GET `/health` returns 200 with healthy status for DB and Redis
- [ ] **PERF-05** Prometheus metrics endpoint (`/metrics`) returns valid Prometheus text format

---

## 12. UI / UX Smoke Tests

- [ ] **UI-01** Application is usable on 1280×800 desktop viewport (no overflow, clipped text, or broken layout)
- [ ] **UI-02** PrimeNG ConfirmDialog appears centered with overlay backdrop for destructive actions
- [ ] **UI-03** Paginator controls (previous/next/page numbers) are visible and functional on subscriptions page
- [ ] **UI-04** p-autoComplete category field shows dropdown suggestions on focus/type
- [ ] **UI-05** Export buttons show correct loading state and disable during download
- [ ] **UI-06** All form validation errors are visually distinct and describe the issue clearly
- [ ] **UI-07** Notification badge count renders correctly (no "0" badge when all are read)
- [ ] **UI-08** After logout and re-login, all UI state is reset (no stale data from previous session)

---

## 13. Security Smoke Tests

- [ ] **SEC-01** Direct navigation to `/dashboard` when not logged in → redirected to `/auth`
- [ ] **SEC-02** Access token is NOT stored in localStorage or sessionStorage (in-memory signal only)
- [ ] **SEC-03** Refresh token cookie has `HttpOnly` flag (not accessible via `document.cookie`)
- [ ] **SEC-04** CORS rejects requests from an unlisted origin (check browser Network tab)
- [ ] **SEC-05** Response headers do not leak sensitive server version info (no `X-Powered-By`, no `Server: Ktor`)
- [ ] **SEC-06** Passwords are not returned in any API response (GET /team, GET /profile, etc.)
- [ ] **SEC-07** SQL injection attempt in vendor name filter → sanitized, returns empty results or normal error (not 500)

---

## Appendix: Test Environment Setup

1. Run `docker-compose up -d` to start PostgreSQL and Redis
2. Start backend: `./gradlew run` (or via IntelliJ)
3. Start frontend: `ng serve` (proxy.conf.json routes API calls to backend)
4. Seed test data: use POST `/register` to create a test company with admin credentials
5. For Stripe tests: use Stripe CLI `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe`
6. For email tests: configure SMTP to MailHog or similar local trap

## Appendix: Regression Scope by Completed Task

| Task    | Regression focus |
|---------|-----------------|
| AUTH-01 | TKN-01 to TKN-05, LOG-01 to LOG-05 |
| ANA-02  | ANA-01 to ANA-09, PERF-04 |
| ANA-04  | ALRT-04 to ALRT-06, NOT-05 to NOT-06 |
| BIL-03  | BIL-03, BIL-06 |
| SUB-01  | SUB-D01 to SUB-D07, ALRT-08 |
| SUB-02  | FIL-01 to FIL-15 |
| SUB-03  | EXP-01 to EXP-08 |
| CLEAN-02| ANA-06, TEAM-06 to TEAM-09 |
| GROWTH-02| CAT-01 to CAT-04 |
