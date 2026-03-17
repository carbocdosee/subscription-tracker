import { Injectable, computed, signal } from "@angular/core";
import { Router } from "@angular/router";
import { AuthTokenService } from "./auth-token.service";

const JWT_EXP_SKEW_SECONDS = 10;

@Injectable({ providedIn: "root" })
export class AuthSessionService {
  private readonly tokenState = signal<string | null>(this.sanitizeToken(this.tokenService.token));
  readonly isAuthenticated = computed(() => Boolean(this.tokenState()));
  readonly currentUserRole = computed((): "ADMIN" | "EDITOR" | "VIEWER" | null => {
    const token = this.tokenState();
    if (!token) return null;
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    try {
      const payload = JSON.parse(this.base64UrlDecode(parts[1])) as { role?: string };
      const role = payload.role;
      if (role === "ADMIN" || role === "EDITOR" || role === "VIEWER") return role;
      return null;
    } catch {
      return null;
    }
  });

  constructor(
    private readonly tokenService: AuthTokenService,
    private readonly router: Router
  ) {
    const sanitized = this.sanitizeToken(this.tokenService.token);
    this.tokenState.set(sanitized);
    this.tokenService.token = sanitized;
  }

  setToken(token: string): void {
    const sanitized = this.sanitizeToken(token);
    if (!sanitized) {
      this.clearToken();
      return;
    }
    this.tokenService.token = sanitized;
    this.tokenState.set(sanitized);
  }

  clearToken(): void {
    this.tokenService.token = null;
    this.tokenState.set(null);
  }

  currentToken(): string | null {
    // Return token as-is; the server validates expiry via 401,
    // which the auth interceptor handles with a silent refresh.
    // Clearing tokenState here would cause premature nav flicker.
    return this.tokenState();
  }

  handleUnauthorized(redirectUrl?: string): void {
    this.clearToken();
    const currentUrl = this.router.url;
    if (currentUrl.startsWith("/auth")) {
      return;
    }
    const redirect = redirectUrl?.trim() || currentUrl;
    void this.router.navigate(["/auth"], {
      queryParams: redirect && redirect !== "/auth" ? { redirect } : undefined
    });
  }

  logout(): void {
    this.clearToken();
    void this.router.navigateByUrl("/auth");
  }

  private sanitizeToken(token: string | null): string | null {
    if (!token) {
      return null;
    }
    return this.isTokenValid(token) ? token : null;
  }

  private isTokenValid(token: string): boolean {
    const parts = token.split(".");
    if (parts.length !== 3) {
      return false;
    }

    try {
      const payload = JSON.parse(this.base64UrlDecode(parts[1])) as { exp?: unknown };
      if (typeof payload.exp !== "number" || !Number.isFinite(payload.exp)) {
        return false;
      }
      const nowSeconds = Math.floor(Date.now() / 1000);
      return payload.exp > nowSeconds + JWT_EXP_SKEW_SECONDS;
    } catch {
      return false;
    }
  }

  private base64UrlDecode(payload: string): string {
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    return atob(padded);
  }
}
