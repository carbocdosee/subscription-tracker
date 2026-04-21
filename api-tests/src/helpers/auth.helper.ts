import { faker } from '@faker-js/faker';
import { api } from './client';

export interface TestUser {
    email: string;
    password: string;
    accessToken: string;
    companyId: string;
    /** Raw "refresh_token=VALUE" cookie string extracted from Set-Cookie header */
    refreshCookie: string;
}

/**
 * Registers a brand-new company + admin user and returns auth data.
 * Each call creates an isolated tenant — safe to run in parallel.
 */
export async function registerAndLogin(): Promise<TestUser> {
    const email = faker.internet.email().toLowerCase();
    const password = 'Test@12345';

    const res = await api.post('/api/auth/register', {
        email,
        password,
        fullName: faker.person.fullName(),
        companyName: faker.company.name(),
        companyDomain: faker.internet.domainName(),
        gdprConsent: true,
    });

    if (res.status !== 201) {
        throw new Error(`Register failed ${res.status}: ${JSON.stringify(res.data)}`);
    }

    return {
        email,
        password,
        accessToken: res.data.accessToken as string,
        companyId: res.data.user.companyId as string,
        refreshCookie: extractRefreshCookie(res.headers['set-cookie']),
    };
}

/** Returns { Authorization: 'Bearer <token>' } header object */
export function authHeader(token: string): Record<string, string> {
    return { Authorization: `Bearer ${token}` };
}

/**
 * Extracts the "refresh_token=VALUE" portion from the Set-Cookie header array.
 * The backend sets secure=true, so in HTTP test environments we forward it manually.
 */
export function extractRefreshCookie(setCookie: string | string[] | undefined): string {
    if (!setCookie) return '';
    const cookies = Array.isArray(setCookie) ? setCookie : [setCookie];
    const found = cookies.find(c => c.startsWith('refresh_token='));
    return found ? found.split(';')[0] : '';
}
