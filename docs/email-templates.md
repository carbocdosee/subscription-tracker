# Email Templates

## Provider
- **Local development:** SMTP via Stalwart (bundled in docker-compose)
- **Production (recommended):** Resend — set `EMAIL_PROVIDER=resend` (see README → Email Setup)
- **Production (alternative):** Any SMTP provider (SendGrid, Postmark, Mailgun, AWS SES) — set `EMAIL_PROVIDER=smtp`
- Source:
  - `backend/src/main/kotlin/com/saastracker/transport/email/EmailService.kt`
  - `backend/src/main/kotlin/com/saastracker/transport/email/SmtpEmailService.kt`

## Templates

### Renewal Digest
- Function: `buildRenewalDigestEmail(company, subscriptions)`
- Includes:
  - Company branding header
  - Summary: number of renewals + total due
  - Per-subscription rows:
    - logo
    - vendor
    - amount
    - renewal date
    - days left
    - CTA links (`Review`, `Set Reminder`, `Cancel`)
  - Footer with unsubscribe link
- Responsive behavior:
  - single-column mobile-friendly card layout
  - fluid-width table inside max-width container

### Team Invitation
- Trigger: admin invites member
- Includes:
  - invitation CTA button
  - one-click accept token URL
  - expiration notice (7 days)

## Preview Notes
- Gmail: table layout and CTA buttons render correctly.
- Outlook: avoids unsupported CSS properties; uses inline styles.
