# Security Incident Response Plan

> Aligned with GDPR Article 33 (72-hour breach notification) and Article 34 (communication to data subjects).

---

## 1. Incident severity classification

| Severity | Definition | Examples |
|---|---|---|
| **P1 — Critical** | Unauthorised access to personal data; active exploitation of a vulnerability | Database dump exfiltration, admin account compromise, ransomware |
| **P2 — High** | Potential exposure of personal data; vulnerability with no confirmed exploitation | Misconfigured access control, leaked `.env`, dependency with known CVE |
| **P3 — Medium** | No personal data exposed; service degradation or DoS | Redis outage, SMTP relay abuse, brute-force attempts |
| **P4 — Low** | No personal data risk; informational | Log noise, performance regression |

---

## 2. Detection sources

- Structured JSON application logs (backend `logback.xml`)
- PostgreSQL audit log (`audit_log` table — actions performed by users)
- Nginx access logs
- Stripe webhook events
- Manual discovery (user report, pen-test finding)

---

## 3. Response procedure

### Phase 1 — Identification (0–2 hours)
1. Confirm the incident is genuine (not a false positive).
2. Assign an incident owner.
3. Record: timestamp of discovery, initial scope, systems affected.

### Phase 2 — Containment (2–8 hours)
1. Revoke all active sessions: run `UPDATE refresh_tokens SET revoked_at = NOW() WHERE revoked_at IS NULL;`.
2. If credentials are compromised: rotate `JWT_SECRET`, `DATABASE_PASSWORD`, `REDIS_PASSWORD` and redeploy.
3. If a vulnerability is exploited: disable the affected endpoint via Nginx (`deny all;`) until patched.
4. Preserve logs: copy PostgreSQL logs and `audit_log` before any cleanup.

### Phase 3 — Assessment (8–24 hours)
1. Determine: what data was accessed, by whom, for how long.
2. Identify affected user IDs from `audit_log` and `email_deliveries`.
3. Assess likelihood of harm to data subjects.

### Phase 4 — Notification (24–72 hours)
#### 4a — Supervisory authority (Art. 33)
If personal data was breached and the breach is likely to result in a risk to the rights and freedoms of natural persons:
- Notify the competent Data Protection Authority **within 72 hours** of becoming aware.
- Include: nature of breach, categories and approximate number of data subjects affected, likely consequences, measures taken.

#### 4b — Data subjects (Art. 34)
If the breach is likely to result in **high risk** to individuals:
- Notify affected users via email without undue delay.
- Use `EmailService.sendPasswordResetEmail()` flow for credential resets if passwords may have been exposed.
- Message template: describe the incident in plain language, state what data was involved, what action the user should take.

### Phase 5 — Recovery
1. Apply patches and redeploy.
2. Force password resets for affected accounts if credentials were compromised.
3. Verify `DataRetentionJob` is running after recovery.
4. Restore from backup if data integrity is compromised.

### Phase 6 — Post-incident review (within 2 weeks)
1. Root cause analysis.
2. Update this document and relevant controls.
3. Create GitHub issue / ticket to track remediation.

---

## 4. Key contacts

| Role | Responsibility |
|---|---|
| Incident Owner | Coordinates response; primary contact for DPA notification |
| Backend Engineer | Log analysis, credential rotation, patch deployment |
| DPO / Legal | Determines notification obligation; drafts communications to DPA and users |

*(Fill in actual names and contact details for your organisation.)*

---

## 5. Credential rotation runbook

```bash
# 1. Generate new secrets
JWT_SECRET=$(openssl rand -base64 48)
REDIS_PASSWORD=$(openssl rand -hex 32)
DB_PASSWORD=$(openssl rand -hex 32)

# 2. Update .env
# Edit JWT_SECRET, REDIS_PASSWORD, REDIS_URL, DATABASE_PASSWORD, POSTGRES_PASSWORD

# 3. Rebuild and restart
docker compose down
docker compose up -d

# 4. Revoke all refresh tokens (old JWT_SECRET already invalidates access tokens)
# psql -c "UPDATE refresh_tokens SET revoked_at = NOW() WHERE revoked_at IS NULL;"
```

---

## 6. Useful queries for incident investigation

```sql
-- All actions by a specific user in the last 30 days
SELECT action, entity_type, entity_id, created_at
FROM audit_log
WHERE user_id = '<uuid>' AND created_at > NOW() - INTERVAL '30 days'
ORDER BY created_at DESC;

-- All active sessions (non-revoked refresh tokens)
SELECT id, user_id, expires_at, created_at
FROM refresh_tokens
WHERE revoked_at IS NULL AND expires_at > NOW();

-- Recent email deliveries to a specific address
SELECT template_type, status, created_at
FROM email_deliveries
WHERE recipient_email = 'user@example.com'
ORDER BY created_at DESC
LIMIT 20;
```

---

## 7. Vulnerability disclosure

If you discover a security vulnerability, please report it responsibly to the operator of this instance
before public disclosure. Do not exploit vulnerabilities to access data beyond what is necessary to
demonstrate the issue.
