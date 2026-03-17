import { faker } from '@faker-js/faker';

export function subscriptionPayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
    return {
        vendorName: faker.company.name(),
        vendorUrl: `https://${faker.internet.domainName()}`,
        category: 'productivity',
        amount: '49.00',
        currency: 'USD',
        billingCycle: 'MONTHLY',
        renewalDate: '2026-12-01',
        autoRenews: true,
        status: 'ACTIVE',
        paymentMode: 'AUTO',
        ...overrides,
    };
}
