import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';
import { subscriptionPayload } from '../helpers/fixtures';

describe('GET /subscriptions/export', () => {
    let user: TestUser;

    beforeAll(async () => {
        user = await registerAndLogin();
        // Seed at least one subscription so export is non-empty
        await api.post('/api/v1/subscriptions',
            subscriptionPayload({ vendorName: 'ExportTestVendor' }),
            { headers: authHeader(user.accessToken) });
    });

    it('format=csv returns 200 with Content-Type text/csv', async () => {
        const res = await api.get('/api/v1/subscriptions/export?format=csv', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.headers['content-type']).toContain('text/csv');
        expect(res.headers['content-disposition']).toContain('subscriptions.csv');
    });

    it('CSV export body contains header row', async () => {
        const res = await api.get('/api/v1/subscriptions/export?format=csv', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        const body = res.data as string;
        // Expect a CSV header line
        expect(typeof body).toBe('string');
        expect(body.length).toBeGreaterThan(0);
        const firstLine = body.split('\n')[0];
        expect(firstLine.toLowerCase()).toContain('vendor');
    });

    it('format=pdf returns 200 with Content-Type application/pdf', async () => {
        const res = await api.get('/api/v1/subscriptions/export?format=pdf', {
            headers: authHeader(user.accessToken),
            responseType: 'arraybuffer',
        });
        expect(res.status).toBe(200);
        expect(res.headers['content-type']).toContain('application/pdf');
        expect(res.headers['content-disposition']).toContain('subscriptions.pdf');
    });

    it('PDF export starts with PDF magic bytes %PDF', async () => {
        const res = await api.get('/api/v1/subscriptions/export?format=pdf', {
            headers: authHeader(user.accessToken),
            responseType: 'arraybuffer',
        });
        expect(res.status).toBe(200);
        const buf = Buffer.from(res.data as ArrayBuffer);
        expect(buf.slice(0, 4).toString()).toBe('%PDF');
    });

    it('format=csv respects filter params', async () => {
        const res = await api.get('/api/v1/subscriptions/export?format=csv&vendor=ExportTestVendor', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(200);
        expect(res.data as string).toContain('ExportTestVendor');
    });

    it('unknown format returns 400', async () => {
        const res = await api.get('/api/v1/subscriptions/export?format=xlsx', {
            headers: authHeader(user.accessToken),
        });
        expect(res.status).toBe(400);
    });
});
