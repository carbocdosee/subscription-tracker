# User Guide

## 1. Register Company
1. Open `/auth/register` via UI onboarding.
2. Provide company name/domain, admin user, password.
3. Login and open dashboard.

## 2. Add and Manage Subscriptions
1. Go to `Subscriptions`.
2. Fill vendor, category, amount, billing cycle, renewal date.
3. Choose payment mode:
   - `Automatic` for auto-charged subscriptions.
   - `Manual` for invoices/bank transfer workflows.
4. For manual mode, set `Next Payment Date`.
5. Save.
6. Use `Mark as Paid` in the table for manual subscriptions to record payment and advance next due date automatically.
7. Optional:
   - import CSV (`vendor_name,category,amount,currency,billing_cycle,renewal_date,...`)
   - assign owner
   - add comments and notes.

## 3. Review Dashboard
- Track:
  - monthly/annual spend
  - renewals in next 30 days
  - duplicates and potential savings.

## 4. Analytics
- Open `Analytics`:
  - YoY spend
  - fastest-growing tools
  - budget vs actual
  - cost per employee

## 5. Team Collaboration
1. Admin opens `Team`.
2. Sends invite with role.
3. Member accepts invite link.
4. Admin can adjust role later.

## 6. Alerts
- Daily scheduler checks renewals at 09:00 UTC.
- Alerts are sent for:
  - 90
  - 60
  - 30
  - 7
  - 1 day before renewal.
- Alerts are idempotent (no duplicate threshold email per renewal cycle).

## 7. Notification Center
1. Click the bell icon in the top-right header.
2. Review unified notifications:
   - renewals due soon
   - manual payment due/overdue
   - invitation email delivery issues
   - invitations expiring soon
3. Use:
   - `Mark all read` to clear unread state
   - per-item check icon to mark a single notification as read
   - click an item to open its target page (`/subscriptions`, `/team`, `/dashboard`).
