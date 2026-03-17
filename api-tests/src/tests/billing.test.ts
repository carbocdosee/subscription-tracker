import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';

/**
 * Billing tests require Stripe to be configured.
 * In environments without a real Stripe API key, checkout/portal endpoints will fail.
 * GET /billing/status always works (no Stripe call needed).
 */
describe('Billing API', () => {
    let user: TestUser;

    beforeAll(async () => {
        user = await registerAndLogin();
    });

    describe('GET /billing/status', () => {
        it('returns 200 with subscriptionStatus for ADMIN', async () => {
            const res = await api.get('/api/v1/billing/status', {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('subscriptionStatus');
            expect(['TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED']).toContain(res.data.subscriptionStatus);
        });

        it('newly registered company is in TRIAL', async () => {
            const res = await api.get('/api/v1/billing/status', {
                headers: authHeader(user.accessToken),
            });
            expect(res.data.subscriptionStatus).toBe('TRIAL');
        });
    });

    describe('POST /billing/checkout', () => {
        it('returns 200 with checkoutUrl when Stripe is configured', async () => {
            const res = await api.post('/api/v1/billing/checkout?plan=monthly', {}, {
                headers: authHeader(user.accessToken),
            });
            // May return 200 (Stripe configured) or 502 (Stripe not configured in test env)
            expect([200, 502]).toContain(res.status);
            if (res.status === 200) {
                expect(res.data).toHaveProperty('checkoutUrl');
                expect(typeof res.data.checkoutUrl).toBe('string');
            }
        });
    });

    describe('GET /billing/portal', () => {
        it('returns 200 with portalUrl or 502 when Stripe not configured', async () => {
            const res = await api.get('/api/v1/billing/portal', {
                headers: authHeader(user.accessToken),
            });
            expect([200, 502]).toContain(res.status);
            if (res.status === 200) {
                expect(res.data).toHaveProperty('portalUrl');
                expect(typeof res.data.portalUrl).toBe('string');
            }
        });
    });

    describe('Role enforcement', () => {
        it('billing endpoints return 401 without auth', async () => {
            const res = await api.get('/api/v1/billing/status');
            expect(res.status).toBe(401);
        });
    });
});
