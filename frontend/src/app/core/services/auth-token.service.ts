import { Injectable, signal } from "@angular/core";

@Injectable({ providedIn: "root" })
export class AuthTokenService {
  private readonly accessToken = signal<string | null>(null);

  get token(): string | null {
    return this.accessToken();
  }

  set token(value: string | null) {
    this.accessToken.set(value);
  }
}
