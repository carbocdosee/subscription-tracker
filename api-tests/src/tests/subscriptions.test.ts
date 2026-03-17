import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';
import { subscriptionPayload } from '../helpers/fixtures';

describe('Subscriptions API — CRUD', () => {
    let user: TestUser;
    let createdId: string;

    beforeAll(async () => {
        user = await registerAndLogin();
    });

    it('POST /subscriptions — creates a subscription (201)', async () => {
        const res = await api.post('/api/v1/subscriptions',
            subscriptionPayload(), { headers: authHeader(user.accessToken) });
        expect(res.status).toBe(201);
        expect(res.data).toHaveProperty('id');
        expect(res.data).toHaveProperty('vendorName');
        expect(res.data).toHaveProperty('amount');
        expect(res.data).toHaveProperty('healthScore');
        createdId = res.data.id as string;
    });

    it('GET /subscriptions — returns paginated list (200)', async () => {
        const res = await api.get('/api/v1/subscriptions', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data).toHaveProperty('items');
        expect(res.data).toHaveProperty('total');
        expect(res.data).toHaveProperty('page');
        expect(res.data).toHaveProperty('size');
        expect(res.data).toHaveProperty('totalPages');
        expect(Array.isArray(res.data.items)).toBe(true);
        expect(res.data.total).toBeGreaterThanOrEqual(1);
    });

    it('GET /subscriptions/:id — returns single subscription (200)', async () => {
        const res = await api.get(`/api/v1/subscriptions/${createdId}`, {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data.id).toBe(createdId);
    });

    it('GET /subscriptions/:id — returns 404 for unknown id', async () => {
        const res = await api.get('/api/v1/subscriptions/00000000-0000-0000-0000-000000000000', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(404);
    });

    it('PUT /subscriptions/:id — updates subscription (200)', async () => {
        const res = await api.put(`/api/v1/subscriptions/${createdId}`,
            subscriptionPayload({ vendorName: 'Updated Vendor' }),
            { headers: authHeader(user.accessToken) });
        expect(res.status).toBe(200);
        expect(res.data.vendorName).toBe('Updated Vendor');
    });

    it('GET /subscriptions/categories — returns predefined and custom categories (200)', async () => {
        const res = await api.get('/api/v1/subscriptions/categories', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(Array.isArray(res.data.predefined)).toBe(true);
        expect(Array.isArray(res.data.custom)).toBe(true);
        expect(res.data.predefined.length).toBeGreaterThan(0);
    });

    it('DELETE /subscriptions/:id — archives subscription, returns 200 {ok:true}', async () => {
        const del = await api.delete(`/api/v1/subscriptions/${createdId}`,
            { headers: authHeader(user.accessToken) });
        expect(del.status).toBe(200);
        expect(del.data).toHaveProperty('ok', true);
    });

    it('archived subscription no longer appears in main list', async () => {
        const list = await api.get('/api/v1/subscriptions', {
            headers: authHeader(user.accessToken),
        });
        const ids = (list.data.items as Array<{ id: string }>).map(s => s.id);
        expect(ids).not.toContain(createdId);
    });

    it('GET /subscriptions/archived — ADMIN sees archived list (200)', async () => {
        const res = await api.get('/api/v1/subscriptions/archived', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(Array.isArray(res.data.items)).toBe(true);
        const archivedIds = (res.data.items as Array<{ id: string }>).map(s => s.id);
        expect(archivedIds).toContain(createdId);
    });

    it('POST /subscriptions — returns 400 on invalid payload', async () => {
        const res = await api.post('/api/v1/subscriptions',
            { vendorName: '', amount: 'not-a-number' },
            { headers: authHeader(user.accessToken) });
        expect(res.status).toBe(400);
    });

    it('mark-paid — POST /subscriptions/:id/mark-paid (200)', async () => {
        // Create a fresh MANUAL subscription to mark as paid
        const create = await api.post('/api/v1/subscriptions',
            subscriptionPayload({ paymentMode: 'MANUAL' }),
            { headers: authHeader(user.accessToken) });
        expect(create.status).toBe(201);
        const id = create.data.id as string;

        const res = await api.post(`/api/v1/subscriptions/${id}/mark-paid`,
            { paidAt: '2026-03-01' },
            { headers: authHeader(user.accessToken) });
        expect(res.status).toBe(200);
        // After marking paid on 2026-03-01 (MONTHLY), nextPaymentDate = 2026-04-01.
        // resolvePaymentStatus(MANUAL, 2026-04-01, today) = PENDING (future date).
        expect(res.data.paymentStatus).toBe('PENDING');
        expect(res.data.lastPaidAt).toBe('2026-03-01');
    });
});
