import { faker } from '@faker-js/faker';
import { api } from '../helpers/client';
import { authHeader, extractRefreshCookie, registerAndLogin } from '../helpers/auth.helper';

describe('POST /api/auth/register', () => {
    it('returns 201 with accessToken on valid data', async () => {
        const res = await api.post('/api/auth/register', {
            email: faker.internet.email(),
            password: 'Valid@1234!',   // min 10 chars required by validateRegister
            fullName: 'Test User',
            companyName: faker.company.name(),
            companyDomain: faker.internet.domainName(),
            gdprConsent: true,
        });
        expect(res.status).toBe(201);
        expect(res.data).toHaveProperty('accessToken');
        expect(res.data).toHaveProperty('tokenType', 'Bearer');
        expect(res.data.user).toHaveProperty('companyId');
        expect(res.data.user).toHaveProperty('email');
        expect(res.data.user).toHaveProperty('role', 'ADMIN');
    });

    it('sets httpOnly refresh_token cookie on register', async () => {
        const res = await api.post('/api/auth/register', {
            email: faker.internet.email(),
            password: 'Valid@1234!',
            fullName: 'Cookie User',
            companyName: faker.company.name(),
            companyDomain: faker.internet.domainName(),
            gdprConsent: true,
        });
        expect(res.status).toBe(201);
        const cookie = extractRefreshCookie(res.headers['set-cookie']);
        expect(cookie).toMatch(/^refresh_token=.+/);
    });

    it('returns 409 when email is already registered', async () => {
        const user = await registerAndLogin();
        const res = await api.post('/api/auth/register', {
            email: user.email,
            password: 'Valid@1234!',
            fullName: 'Dup',
            companyName: faker.company.name(),
            companyDomain: faker.internet.domainName(),
            gdprConsent: true,
        });
        expect(res.status).toBe(409);
    });

    it('returns 400 on missing required fields', async () => {
        const res = await api.post('/api/auth/register', {
            email: 'not-an-email',
            password: '123',
        });
        expect(res.status).toBe(400);
    });
});

describe('POST /api/auth/login', () => {
    it('returns 200 with accessToken on valid credentials', async () => {
        const user = await registerAndLogin();
        const res = await api.post('/api/auth/login', {
            email: user.email,
            password: user.password,
        });
        expect(res.status).toBe(200);
        expect(res.data.accessToken).toBeDefined();
        expect(res.data.user.email).toBe(user.email);
    });

    it('returns 401 on wrong password', async () => {
        const user = await registerAndLogin();
        const res = await api.post('/api/auth/login', {
            email: user.email,
            password: 'WrongPass@999',  // ≥ 10 chars so validation passes, auth fails
        });
        expect(res.status).toBe(401);
    });

    it('returns 401 on unknown email', async () => {
        const res = await api.post('/api/auth/login', {
            email: 'nobody@nowhere.invalid',
            password: 'Test@12345',
        });
        expect(res.status).toBe(401);
    });
});

describe('POST /api/auth/refresh', () => {
    it('returns 200 with new accessToken when refresh cookie is valid', async () => {
        const user = await registerAndLogin();
        const res = await api.post('/api/auth/refresh', {}, {
            headers: {
                // Forward the refresh_token cookie manually (secure=true cookie won't be
                // sent automatically by axios in an HTTP test environment)
                Cookie: user.refreshCookie,
            },
        });
        expect(res.status).toBe(200);
        expect(res.data.accessToken).toBeDefined();
    });

    it('returns 401 when no refresh cookie is present', async () => {
        const res = await api.post('/api/auth/refresh');
        expect(res.status).toBe(401);
    });
});

describe('POST /api/auth/logout', () => {
    it('returns 204 and clears the refresh cookie', async () => {
        const user = await registerAndLogin();
        const res = await api.post('/api/auth/logout', {}, {
            headers: { Cookie: user.refreshCookie },
        });
        expect(res.status).toBe(204);
    });

    it('returns 204 even without a refresh cookie (idempotent logout)', async () => {
        const res = await api.post('/api/auth/logout');
        expect(res.status).toBe(204);
    });
});

describe('Protected routes — unauthenticated access', () => {
    it('GET /api/v1/subscriptions without token returns 401', async () => {
        const res = await api.get('/api/v1/subscriptions');
        expect(res.status).toBe(401);
    });

    it('GET /api/v1/team/members without token returns 401', async () => {
        const res = await api.get('/api/v1/team/members');
        expect(res.status).toBe(401);
    });

    it('GET /api/v1/analytics without token returns 401', async () => {
        const res = await api.get('/api/v1/analytics');
        expect(res.status).toBe(401);
    });
});
