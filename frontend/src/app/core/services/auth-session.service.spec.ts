import { AuthSessionService } from "./auth-session.service";

class MockTokenService {
  private value: string | null;

  constructor(initial: string | null) {
    this.value = initial;
  }

  get token(): string | null {
    return this.value;
  }

  set token(next: string | null) {
    this.value = next;
  }
}

class MockRouter {
  url = "/dashboard";
  readonly navigateByUrl = jest.fn<Promise<boolean>, [string]>().mockResolvedValue(true);
  readonly navigate = jest.fn<Promise<boolean>, [string[], { queryParams?: Record<string, string> }?]>().mockResolvedValue(true);
}

const base64UrlEncode = (value: string): string =>
  btoa(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");

const buildJwt = (expSeconds: number): string => {
  const header = base64UrlEncode(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64UrlEncode(JSON.stringify({ exp: expSeconds }));
  return `${header}.${payload}.signature`;
};

describe("AuthSessionService", () => {
  it("treats valid token as authenticated", () => {
    const validToken = buildJwt(Math.floor(Date.now() / 1000) + 3600);
    const tokenService = new MockTokenService(validToken);
    const router = new MockRouter();

    const service = new AuthSessionService(tokenService as never, router as never);

    if (!service.isAuthenticated()) {
      throw new Error("Valid token should be authenticated");
    }
    if (service.currentToken() !== validToken) {
      throw new Error("Valid token should be preserved");
    }
    if (tokenService.token !== validToken) {
      throw new Error("Token service should keep valid token");
    }
  });

  it("clears expired token on startup", () => {
    const expiredToken = buildJwt(Math.floor(Date.now() / 1000) - 3600);
    const tokenService = new MockTokenService(expiredToken);
    const router = new MockRouter();

    const service = new AuthSessionService(tokenService as never, router as never);

    if (service.isAuthenticated()) {
      throw new Error("Expired token should not be authenticated");
    }
    if (service.currentToken() !== null) {
      throw new Error("Expired token should be cleared from session");
    }
    if (tokenService.token !== null) {
      throw new Error("Expired token should be cleared from storage");
    }
  });

  it("redirects to auth and clears token on unauthorized", () => {
    const validToken = buildJwt(Math.floor(Date.now() / 1000) + 3600);
    const tokenService = new MockTokenService(validToken);
    const router = new MockRouter();
    router.url = "/subscriptions";

    const service = new AuthSessionService(tokenService as never, router as never);
    service.handleUnauthorized(router.url);

    if (tokenService.token !== null) {
      throw new Error("Unauthorized should clear token from storage");
    }
    if (service.isAuthenticated()) {
      throw new Error("Unauthorized should mark session as logged out");
    }
    if (router.navigate.mock.calls.length !== 1) {
      throw new Error("Unauthorized should redirect to auth once");
    }
    const [pathArg, optionsArg] = router.navigate.mock.calls[0];
    if (JSON.stringify(pathArg) !== JSON.stringify(["/auth"])) {
      throw new Error("Redirect target must be /auth");
    }
    if (JSON.stringify(optionsArg) !== JSON.stringify({ queryParams: { redirect: "/subscriptions" } })) {
      throw new Error("Redirect query params are incorrect");
    }
  });
});
