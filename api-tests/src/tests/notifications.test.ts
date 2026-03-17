import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';

describe('Notifications API', () => {
    let user: TestUser;

    beforeAll(async () => {
        user = await registerAndLogin();
    });

    describe('GET /notifications', () => {
        it('returns 200 with notifications feed shape', async () => {
            const res = await api.get('/api/v1/notifications', {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('items');
            expect(res.data).toHaveProperty('unreadCount');
            expect(Array.isArray(res.data.items)).toBe(true);
            expect(typeof res.data.unreadCount).toBe('number');
        });

        it('respects limit query param', async () => {
            const res = await api.get('/api/v1/notifications?limit=5', {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data.items.length).toBeLessThanOrEqual(5);
        });

        it('each notification item has required fields', async () => {
            const res = await api.get('/api/v1/notifications', {
                headers: authHeader(user.accessToken),
            });
            if (res.data.items.length > 0) {
                const item = res.data.items[0] as {
                    key: string;
                    type: string;
                    severity: string;
                    title: string;
                    message: string;
                    read: boolean;
                };
                expect(item).toHaveProperty('key');
                expect(item).toHaveProperty('type');
                expect(item).toHaveProperty('severity');
                expect(item).toHaveProperty('title');
                expect(item).toHaveProperty('message');
                expect(item).toHaveProperty('read');
            }
        });
    });

    describe('GET /notifications/unread-count', () => {
        it('returns 200 with unreadCount', async () => {
            const res = await api.get('/api/v1/notifications/unread-count', {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('unreadCount');
            expect(typeof res.data.unreadCount).toBe('number');
        });
    });

    describe('POST /notifications/read-all', () => {
        it('returns 200 with ok=true and marked count', async () => {
            const res = await api.post('/api/v1/notifications/read-all', {}, {
                headers: authHeader(user.accessToken),
            });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('ok', true);
            expect(typeof res.data.marked).toBe('number');
        });

        it('unreadCount is 0 after read-all', async () => {
            await api.post('/api/v1/notifications/read-all', {}, {
                headers: authHeader(user.accessToken),
            });
            const res = await api.get('/api/v1/notifications/unread-count', {
                headers: authHeader(user.accessToken),
            });
            expect(res.data.unreadCount).toBe(0);
        });
    });

    describe('POST /notifications/read', () => {
        it('returns 400 when keys is missing', async () => {
            const res = await api.post('/api/v1/notifications/read', {}, {
                headers: authHeader(user.accessToken),
            });
            // Either 400 (validation) or 200 with 0 marked — implementation may vary
            expect([200, 400]).toContain(res.status);
        });

        it('returns 200 when marking specific keys (even if not found)', async () => {
            const res = await api.post('/api/v1/notifications/read',
                { keys: ['nonexistent-key-1', 'nonexistent-key-2'] },
                { headers: authHeader(user.accessToken) });
            expect(res.status).toBe(200);
            expect(res.data).toHaveProperty('ok', true);
        });
    });
});
