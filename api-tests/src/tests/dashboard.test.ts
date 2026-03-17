import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';
import { subscriptionPayload } from '../helpers/fixtures';

describe('Dashboard API', () => {
    let user: TestUser;

    beforeAll(async () => {
        user = await registerAndLogin();
        // Seed subscriptions for meaningful dashboard data
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ amount: '200.00', renewalDate: '2026-04-01' }),
            { headers: authHeader(user.accessToken) });
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ amount: '50.00', category: 'security', renewalDate: '2026-12-31' }),
            { headers: authHeader(user.accessToken) });
    });

    describe('GET /dashboard/stats', () => {
        it('returns 200 with dashboard stats shape', async () => {
            const res = await api.get('/api/v1/dashboard/stats', {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('totalMonthlySpend');
            expect(res.data).toHaveProperty('totalAnnualSpend');
            expect(res.data).toHaveProperty('subscriptionCount');
            expect(res.data).toHaveProperty('renewingIn30Days');
            expect(res.data).toHaveProperty('spendByCategory');
            expect(res.data).toHaveProperty('monthlyTrend');
            expect(res.data).toHaveProperty('topCostDrivers');
            expect(res.data).toHaveProperty('duplicateWarnings');
            expect(res.data).toHaveProperty('potentialSavings');
        });

        it('subscriptionCount matches actual seeded count', async () => {
            const res = await api.get('/api/v1/dashboard/stats', {
                headers: authHeader(user.accessToken),
            });
            expect(res.data.subscriptionCount).toBeGreaterThanOrEqual(2);
        });

        it('renewingIn30Days is an array', async () => {
            const res = await api.get('/api/v1/dashboard/stats', {
                headers: authHeader(user.accessToken),
            });
            expect(Array.isArray(res.data.renewingIn30Days)).toBe(true);
        });

        it('spendByCategory is an object', async () => {
            const res = await api.get('/api/v1/dashboard/stats', {
                headers: authHeader(user.accessToken),
            });
            expect(typeof res.data.spendByCategory).toBe('object');
            expect(res.data.spendByCategory).not.toBeNull();
        });

        it('monthlyTrend is an array', async () => {
            const res = await api.get('/api/v1/dashboard/stats', {
                headers: authHeader(user.accessToken),
            });
            expect(Array.isArray(res.data.monthlyTrend)).toBe(true);
        });

        it('topCostDrivers is an array', async () => {
            const res = await api.get('/api/v1/dashboard/stats', {
                headers: authHeader(user.accessToken),
            });
            expect(Array.isArray(res.data.topCostDrivers)).toBe(true);
        });
    });
});
