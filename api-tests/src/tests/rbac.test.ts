/**
 * RBAC & Multi-Tenant Isolation Tests
 *
 * Covers:
 *  - VIEWER role cannot mutate subscriptions (403)
 *  - VIEWER cannot access ADMIN-only endpoints (403)
 *  - Multi-tenant isolation: Company B user cannot read Company A data (404)
 *
 * Invite flow (no email required):
 *   1. Admin invites user → response contains `acceptInviteUrl` with the token embedded
 *   2. Parse token from URL
 *   3. POST /api/auth/accept-invite → receive accessToken for the invited user
 */

import { faker } from '@faker-js/faker';
import { api } from '../helpers/client';
import { authHeader, extractRefreshCookie, registerAndLogin, TestUser } from '../helpers/auth.helper';
import { subscriptionPayload } from '../helpers/fixtures';

// ─── helpers ────────────────────────────────────────────────────────────────

/**
 * Invites a user with the given role, accepts the invitation using the token
 * returned in the API response (no email required), and returns auth data.
 */
async function inviteAndAccept(
    adminToken: string,
    role: 'EDITOR' | 'VIEWER',
): Promise<TestUser> {
    const email = faker.internet.email();

    const invite = await api.post(
        '/api/v1/team/invite',
        { email, role },
        { headers: authHeader(adminToken) },
    );
    expect(invite.status).toBe(201);

    // acceptInviteUrl = "http://host/accept-invite?token=<TOKEN>" (single query param)
    const acceptInviteUrl: string = invite.data.acceptInviteUrl;
    const token = acceptInviteUrl.split('?token=')[1] ?? '';
    expect(token.length).toBeGreaterThan(0);

    const accept = await api.post('/api/auth/accept-invite', {
        token,
        fullName: faker.person.fullName(),
        password: 'Test@12345',   // ≥ 10 chars (validateAcceptInvite min=10)
    });
    expect(accept.status).toBe(200);

    return {
        email,
        password: 'Test@12345',
        accessToken: accept.data.accessToken as string,
        companyId: accept.data.user.companyId as string,
        refreshCookie: extractRefreshCookie(accept.headers['set-cookie']),
    };
}

// ─── VIEWER role restrictions ────────────────────────────────────────────────

describe('RBAC — VIEWER role restrictions', () => {
    let admin: TestUser;
    let viewer: TestUser;
    let existingSubId: string;

    beforeAll(async () => {
        admin = await registerAndLogin();

        // Admin creates a subscription so VIEWER has something to try mutating
        const sub = await api.post(
            '/api/v1/subscriptions',
            subscriptionPayload(),
            { headers: authHeader(admin.accessToken) },
        );
        expect(sub.status).toBe(201);
        existingSubId = sub.data.id as string;

        viewer = await inviteAndAccept(admin.accessToken, 'VIEWER');
    });

    it('VIEWER can read the subscription list (200)', async () => {
        const res = await api.get('/api/v1/subscriptions', {
            headers: authHeader(viewer.accessToken),
        });
        expect(res.status).toBe(200);
        expect(Array.isArray(res.data.items)).toBe(true);
    });

    it('VIEWER cannot create a subscription (403)', async () => {
        const res = await api.post(
            '/api/v1/subscriptions',
            subscriptionPayload(),
            { headers: authHeader(viewer.accessToken) },
        );
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot update a subscription (403)', async () => {
        const res = await api.put(
            `/api/v1/subscriptions/${existingSubId}`,
            subscriptionPayload({ vendorName: 'Hacked Vendor' }),
            { headers: authHeader(viewer.accessToken) },
        );
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot archive a subscription (403)', async () => {
        const res = await api.delete(
            `/api/v1/subscriptions/${existingSubId}`,
            { headers: authHeader(viewer.accessToken) },
        );
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot access archived subscriptions (403)', async () => {
        const res = await api.get('/api/v1/subscriptions/archived', {
            headers: authHeader(viewer.accessToken),
        });
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot trigger snapshot capture (403)', async () => {
        const res = await api.post('/api/v1/admin/snapshots/capture', {}, {
            headers: authHeader(viewer.accessToken),
        });
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot invite team members (403)', async () => {
        const res = await api.post(
            '/api/v1/team/invite',
            { email: faker.internet.email(), role: 'VIEWER' },
            { headers: authHeader(viewer.accessToken) },
        );
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot patch company settings (403)', async () => {
        const res = await api.patch(
            '/api/v1/company',
            { employeeCount: 999 },
            { headers: authHeader(viewer.accessToken) },
        );
        expect(res.status).toBe(403);
    });

    it('VIEWER cannot mark a subscription as paid (403)', async () => {
        const res = await api.post(
            `/api/v1/subscriptions/${existingSubId}/mark-paid`,
            { paidAt: '2026-03-01' },
            { headers: authHeader(viewer.accessToken) },
        );
        expect(res.status).toBe(403);
    });
});

// ─── EDITOR role — can mutate but NOT admin-only endpoints ──────────────────

describe('RBAC — EDITOR role restrictions', () => {
    let admin: TestUser;
    let editor: TestUser;

    beforeAll(async () => {
        admin = await registerAndLogin();
        editor = await inviteAndAccept(admin.accessToken, 'EDITOR');
    });

    it('EDITOR can create a subscription (201)', async () => {
        const res = await api.post(
            '/api/v1/subscriptions',
            subscriptionPayload(),
            { headers: authHeader(editor.accessToken) },
        );
        expect(res.status).toBe(201);
    });

    it('EDITOR cannot access archived subscriptions (403)', async () => {
        const res = await api.get('/api/v1/subscriptions/archived', {
            headers: authHeader(editor.accessToken),
        });
        expect(res.status).toBe(403);
    });

    it('EDITOR cannot trigger snapshot capture (403)', async () => {
        const res = await api.post('/api/v1/admin/snapshots/capture', {}, {
            headers: authHeader(editor.accessToken),
        });
        expect(res.status).toBe(403);
    });

    it('EDITOR cannot invite team members (403)', async () => {
        const res = await api.post(
            '/api/v1/team/invite',
            { email: faker.internet.email(), role: 'VIEWER' },
            { headers: authHeader(editor.accessToken) },
        );
        expect(res.status).toBe(403);
    });
});

// ─── Multi-tenant isolation ──────────────────────────────────────────────────

describe('Multi-tenant isolation', () => {
    let companyA: TestUser;
    let companyB: TestUser;
    let subOfA: string;

    beforeAll(async () => {
        // Two independent companies — each registerAndLogin() creates a fresh tenant
        companyA = await registerAndLogin();
        companyB = await registerAndLogin();

        const sub = await api.post(
            '/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'CompanyAOnlyVendor' }),
            { headers: authHeader(companyA.accessToken) },
        );
        expect(sub.status).toBe(201);
        subOfA = sub.data.id as string;
    });

    it('Company B cannot read Company A subscription by ID (404)', async () => {
        const res = await api.get(`/api/v1/subscriptions/${subOfA}`, {
            headers: authHeader(companyB.accessToken),
        });
        // The repository filters by companyId, so the subscription is not found
        expect(res.status).toBe(404);
    });

    it("Company B list does not contain Company A's subscriptions", async () => {
        const res = await api.get('/api/v1/subscriptions', {
            headers: authHeader(companyB.accessToken),
        });
        expect(res.status).toBe(200);
        const ids = (res.data.items as Array<{ id: string }>).map(s => s.id);
        expect(ids).not.toContain(subOfA);
    });

    it('Company B cannot update Company A subscription (404)', async () => {
        const res = await api.put(
            `/api/v1/subscriptions/${subOfA}`,
            subscriptionPayload({ vendorName: 'CrossTenantHack' }),
            { headers: authHeader(companyB.accessToken) },
        );
        // update() calls findById(subscriptionId, companyId) → returns null → 404
        expect(res.status).toBe(404);
    });

    it('Company B cannot archive Company A subscription (404)', async () => {
        const res = await api.delete(`/api/v1/subscriptions/${subOfA}`, {
            headers: authHeader(companyB.accessToken),
        });
        // archive() calls findById(subscriptionId, companyId) → returns null → 404
        expect(res.status).toBe(404);
    });

    it('Company B dashboard reflects only its own data', async () => {
        const res = await api.get('/api/v1/dashboard/stats', {
            headers: authHeader(companyB.accessToken),
        });
        expect(res.status).toBe(200);
        // Company B has no subscriptions, so count should be 0
        expect(res.data.subscriptionCount).toBe(0);
    });
});
