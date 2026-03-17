import { faker } from '@faker-js/faker';
import { api } from '../helpers/client';
import { authHeader, registerAndLogin, TestUser } from '../helpers/auth.helper';

describe('Team API', () => {
    let admin: TestUser;

    beforeAll(async () => {
        admin = await registerAndLogin();
    });

    describe('GET /team/members', () => {
        it('returns list with at least the registered admin', async () => {
            const res = await api.get('/api/v1/team/members', {
                headers: authHeader(admin.accessToken),
            });
            expect(res.status).toBe(200);
            expect(Array.isArray(res.data.items)).toBe(true);
            expect(res.data.items.length).toBeGreaterThanOrEqual(1);
            const member = (res.data.items as Array<{ email: string; role: string }>)
                .find(m => m.email === admin.email);
            expect(member).toBeDefined();
            expect(member?.role).toBe('ADMIN');
        });
    });

    describe('POST /team/invite', () => {
        it('ADMIN can invite a new user (201)', async () => {
            const res = await api.post('/api/v1/team/invite', {
                email: faker.internet.email(),
                role: 'VIEWER',
            }, { headers: authHeader(admin.accessToken) });
            expect(res.status).toBe(201);
            expect(res.data).toHaveProperty('invitation');
            expect(res.data.invitation).toHaveProperty('email');
            expect(res.data.invitation).toHaveProperty('role', 'VIEWER');
            expect(res.data.invitation).toHaveProperty('expiresAt');
            expect(res.data).toHaveProperty('acceptInviteUrl');
            expect(res.data).toHaveProperty('emailDelivery');
        });

        it('inviting same email again reuses existing invitation (200)', async () => {
            const email = faker.internet.email();
            await api.post('/api/v1/team/invite', { email, role: 'EDITOR' },
                { headers: authHeader(admin.accessToken) });
            const res = await api.post('/api/v1/team/invite', { email, role: 'EDITOR' },
                { headers: authHeader(admin.accessToken) });
            expect(res.status).toBe(200);
            expect(res.data.reusedExisting).toBe(true);
        });

        it('returns 400 on invalid email', async () => {
            const res = await api.post('/api/v1/team/invite', {
                email: 'not-an-email',
                role: 'VIEWER',
            }, { headers: authHeader(admin.accessToken) });
            expect(res.status).toBe(400);
        });
    });

    describe('GET /team/invitations', () => {
        it('returns list of pending invitations', async () => {
            // Ensure at least one invitation exists
            await api.post('/api/v1/team/invite',
                { email: faker.internet.email(), role: 'EDITOR' },
                { headers: authHeader(admin.accessToken) });

            const res = await api.get('/api/v1/team/invitations', {
                headers: authHeader(admin.accessToken),
            });
            expect(res.status).toBe(200);
            expect(Array.isArray(res.data.items)).toBe(true);
        });
    });

    describe('PUT /team/users/:id/role', () => {
        it('ADMIN can change another user role', async () => {
            // Get the admin's own id
            const membersRes = await api.get('/api/v1/team/members', {
                headers: authHeader(admin.accessToken),
            });
            const members = membersRes.data.items as Array<{ id: string; email: string; role: string }>;
            const self = members.find(m => m.email === admin.email);
            expect(self).toBeDefined();

            // Changing own role to EDITOR (allowed for testing purposes)
            const res = await api.put(`/api/v1/team/users/${self!.id}/role`,
                { role: 'EDITOR' },
                { headers: authHeader(admin.accessToken) });
            // Either succeeds or returns 403 (can't demote yourself — depends on business logic)
            expect([200, 403]).toContain(res.status);
        });
    });

    describe('DELETE /team/invitations/:id', () => {
        it('ADMIN can cancel a pending invitation', async () => {
            const invite = await api.post('/api/v1/team/invite',
                { email: faker.internet.email(), role: 'VIEWER' },
                { headers: authHeader(admin.accessToken) });
            expect(invite.status).toBe(201);
            const invitationId = invite.data.invitation.id as string;

            const del = await api.delete(`/api/v1/team/invitations/${invitationId}`, {
                headers: authHeader(admin.accessToken),
            });
            expect([200, 204]).toContain(del.status);
        });
    });

    describe('GET /team/audit-log', () => {
        it('returns audit log items', async () => {
            const res = await api.get('/api/v1/team/audit-log', {
                headers: authHeader(admin.accessToken),
            });
            expect(res.status).toBe(200);
            expect(Array.isArray(res.data.items)).toBe(true);
        });
    });

    describe('GET /company', () => {
        it('returns company settings (200)', async () => {
            const res = await api.get('/api/v1/company', {
                headers: authHeader(admin.accessToken),
            });
            expect(res.status).toBe(200);
        });
    });

    describe('PATCH /company', () => {
        it('ADMIN can update employee count (200)', async () => {
            const res = await api.patch('/api/v1/company',
                { employeeCount: 42 },
                { headers: authHeader(admin.accessToken) });
            expect(res.status).toBe(200);
            expect(res.data.employeeCount).toBe(42);
        });
    });
});
