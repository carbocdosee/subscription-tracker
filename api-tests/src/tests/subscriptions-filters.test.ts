import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';
import { subscriptionPayload } from '../helpers/fixtures';

describe('GET /subscriptions — pagination and filtering', () => {
    let user: TestUser;

    beforeAll(async () => {
        user = await registerAndLogin();

        // Seed subscriptions with distinct values for filter assertions
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'FilterVendorAlpha', category: 'devtools', amount: '10.00', status: 'ACTIVE', paymentMode: 'MANUAL' }),
            { headers: authHeader(user.accessToken) });
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'FilterVendorBeta', category: 'security', amount: '20.00', status: 'ACTIVE', paymentMode: 'AUTO' }),
            { headers: authHeader(user.accessToken) });
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'FilterVendorGamma', category: 'devtools', amount: '30.00', status: 'CANCELED', paymentMode: 'MANUAL' }),
            { headers: authHeader(user.accessToken) });
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'FilterVendorDelta', category: 'analytics', amount: '40.00', status: 'ACTIVE', paymentMode: 'AUTO' }),
            { headers: authHeader(user.accessToken) });
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'FilterVendorEpsilon', category: 'analytics', amount: '50.00', status: 'ACTIVE', paymentMode: 'AUTO' }),
            { headers: authHeader(user.accessToken) });
    });

    it('returns all subscriptions with default page/size', async () => {
        const res = await api.get('/api/v1/subscriptions', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data.page).toBe(1);
        expect(res.data.size).toBe(25);
    });

    it('filters by vendor (partial, case-insensitive)', async () => {
        const res = await api.get('/api/v1/subscriptions?vendor=filtervendoralpha', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data.total).toBeGreaterThanOrEqual(1);
        const names = (res.data.items as Array<{ vendorName: string }>).map(s => s.vendorName.toLowerCase());
        expect(names.every(n => n.includes('filtervendoralpha'))).toBe(true);
    });

    it('filters by category', async () => {
        const res = await api.get('/api/v1/subscriptions?category=devtools', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data.total).toBeGreaterThanOrEqual(2);
        const categories = (res.data.items as Array<{ category: string }>).map(s => s.category.toLowerCase());
        expect(categories.every(c => c.includes('devtools'))).toBe(true);
    });

    it('filters by status=CANCELED', async () => {
        const res = await api.get('/api/v1/subscriptions?status=CANCELED', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        (res.data.items as Array<{ status: string }>).forEach(s => {
            expect(s.status).toBe('CANCELED');
        });
    });

    it('filters by paymentMode=MANUAL', async () => {
        const res = await api.get('/api/v1/subscriptions?paymentMode=MANUAL', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        (res.data.items as Array<{ paymentMode: string }>).forEach(s => {
            expect(s.paymentMode).toBe('MANUAL');
        });
    });

    it('filters by minAmount and maxAmount', async () => {
        const res = await api.get('/api/v1/subscriptions?minAmount=20&maxAmount=30', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        // All returned items must be in range $20–$30 (amount is a formatted string like "$20.00")
        (res.data.items as Array<{ amount: string }>).forEach(s => {
            const amount = parseFloat(s.amount.replace(/[^0-9.]/g, ''));
            expect(amount).toBeGreaterThanOrEqual(20);
            expect(amount).toBeLessThanOrEqual(30);
        });
    });

    it('pagination: page=1&size=2 returns at most 2 items', async () => {
        const res = await api.get('/api/v1/subscriptions?page=1&size=2', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data.size).toBe(2);
        expect(res.data.items.length).toBeLessThanOrEqual(2);
        expect(res.data.totalPages).toBeGreaterThanOrEqual(1);
    });

    it('pagination: page=2 returns a different set than page=1', async () => {
        const page1 = await api.get('/api/v1/subscriptions?page=1&size=2', {
            headers: authHeader(user.accessToken),
        });
        const page2 = await api.get('/api/v1/subscriptions?page=2&size=2', {
            headers: authHeader(user.accessToken),
        });
        expect(page1.status).toBe(200);
        expect(page2.status).toBe(200);

        if (page1.data.total > 2) {
            const ids1 = (page1.data.items as Array<{ id: string }>).map(s => s.id);
            const ids2 = (page2.data.items as Array<{ id: string }>).map(s => s.id);
            const overlap = ids1.filter(id => ids2.includes(id));
            expect(overlap.length).toBe(0);
        }
    });

    it('sortBy=amount&sortDir=desc returns items in descending amount order', async () => {
        const res = await api.get('/api/v1/subscriptions?sortBy=amount&sortDir=desc&size=100', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        const amounts = (res.data.items as Array<{ amount: string }>)
            .map(s => parseFloat(s.amount.replace(/[^0-9.]/g, '')));
        for (let i = 1; i < amounts.length; i++) {
            expect(amounts[i]).toBeLessThanOrEqual(amounts[i - 1]);
        }
    });

    it('size is clamped to 100 max', async () => {
        const res = await api.get('/api/v1/subscriptions?size=999', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data.size).toBe(100);
    });
});
