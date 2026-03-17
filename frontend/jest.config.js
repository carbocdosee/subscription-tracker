module.exports = {
  preset: "jest-preset-angular",
  testEnvironment: "jsdom",
  roots: ["<rootDir>/src"],
  testMatch: ["**/*.spec.ts"],
  transform: {
    "^.+\\.(ts|mjs|js|html)$": "jest-preset-angular"
  },
  transformIgnorePatterns: ["node_modules/(?!.*\\.mjs$)"],
  moduleFileExtensions: ["ts", "js", "html"],
  setupFilesAfterEnv: ["<rootDir>/setup-jest.ts"]
};
