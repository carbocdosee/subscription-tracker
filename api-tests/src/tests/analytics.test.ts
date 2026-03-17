import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';
import { subscriptionPayload } from '../helpers/fixtures';

describe('Analytics API', () => {
    let user: TestUser;

    beforeAll(async () => {
        user = await registerAndLogin();
        // Seed a subscription so analytics has real data
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ amount: '100.00' }),
            { headers: authHeader(user.accessToken) });
    });

    describe('GET /analytics', () => {
        it('returns 200 with analytics shape', async () => {
            const res = await api.get('/api/v1/analytics', {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('yoySpendComparison');
            expect(res.data).toHaveProperty('budgetGauge');
            expect(res.data).toHaveProperty('costPerEmployee');
            expect(res.data).toHaveProperty('healthScore');
            expect(res.data).toHaveProperty('fastestGrowingSubscriptions');
        });

        it('yoySpendComparison has dataAvailable flag', async () => {
            const res = await api.get('/api/v1/analytics', {
                headers: authHeader(user.accessToken),
            });
            expect(typeof res.data.yoySpendComparison.dataAvailable).toBe('boolean');
        });

        it('costPerEmployee contains employeeCount and amounts', async () => {
            const res = await api.get('/api/v1/analytics', {
                headers: authHeader(user.accessToken),
            });
            const { costPerEmployee } = res.data;
            expect(costPerEmployee).toHaveProperty('employeeCount');
            expect(costPerEmployee).toHaveProperty('monthlyUsd');
            expect(costPerEmployee).toHaveProperty('annualUsd');
        });

        it('healthScore is one of GOOD, WARNING, CRITICAL', async () => {
            const res = await api.get('/api/v1/analytics', {
                headers: authHeader(user.accessToken),
            });
            expect(['GOOD', 'WARNING', 'CRITICAL']).toContain(res.data.healthScore);
        });
    });

    describe('POST /admin/snapshots/capture', () => {
        it('ADMIN can trigger snapshot capture (200)', async () => {
            const res = await api.post('/api/v1/admin/snapshots/capture', {}, {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('ok', true);
        });
    });
});
