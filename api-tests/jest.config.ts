import type { Config } from 'jest';

const config: Config = {
    preset: 'ts-jest',
    testEnvironment: 'node',
    testMatch: ['**/tests/**/*.test.ts'],
    setupFiles: ['dotenv/config'],
    globals: {
        'ts-jest': {
            tsconfig: './tsconfig.json',
        },
    },
    testTimeout: 30000,
    reporters: [
        'default',
        [
            'jest-html-reporters',
            {
                publicPath: './reports',
                filename: 'test-report.html',
                openReport: false,
                pageTitle: 'SaaS Tracker — API Test Report',
                includeFailureMsg: true,
                includeConsoleLog: false,
                expand: true,
            },
        ],
    ],
};

export default config;
