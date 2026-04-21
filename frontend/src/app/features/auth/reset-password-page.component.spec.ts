import { TestBed } from "@angular/core/testing";
import { ActivatedRoute, Router, provideRouter } from "@angular/router";
import { of, throwError } from "rxjs";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ResetPasswordPageComponent } from "./reset-password-page.component";
import { TrackerApiService } from "../../core/services/tracker-api.service";

function buildActivatedRoute(token: string | null): Partial<ActivatedRoute> {
  return {
    snapshot: {
      queryParamMap: { get: (key: string) => (key === "token" ? token : null) }
    } as never
  };
}

function buildApi(overrides: Partial<{ resetPassword: jest.Mock }>): Partial<TrackerApiService> {
  return {
    resetPassword: jest.fn().mockReturnValue(of(undefined)),
    ...overrides
  };
}

function createComponent(token: string | null, api: Partial<TrackerApiService> = buildApi({})) {
  TestBed.configureTestingModule({
    imports: [ResetPasswordPageComponent, NoopAnimationsModule],
    providers: [
      provideRouter([]),
      { provide: TrackerApiService, useValue: api },
      { provide: ActivatedRoute, useValue: buildActivatedRoute(token) }
    ]
  });

  const router = TestBed.inject(Router);
  const navigateSpy = jest.spyOn(router, "navigateByUrl").mockResolvedValue(true);

  const fixture = TestBed.createComponent(ResetPasswordPageComponent);
  fixture.detectChanges();

  return { component: fixture.componentInstance, navigateSpy };
}

describe("ResetPasswordPageComponent", () => {
  afterEach(() => TestBed.resetTestingModule());

  it("redirects to /auth when no token is present in query params", () => {
    const { navigateSpy } = createComponent(null);

    if (navigateSpy.mock.calls.length !== 1) {
      throw new Error("Should redirect exactly once when token is missing");
    }
    if (navigateSpy.mock.calls[0][0] !== "/auth") {
      throw new Error(`Expected redirect to '/auth', got '${navigateSpy.mock.calls[0][0]}'`);
    }
  });

  it("does not redirect when a token is present", () => {
    const { navigateSpy } = createComponent("valid-token-abc");

    if (navigateSpy.mock.calls.length !== 0) {
      throw new Error("Should not redirect when token is present");
    }
  });

  it("does not call API and marks form touched when password is too short", () => {
    const api = buildApi({});
    const { component } = createComponent("valid-token-abc", api);

    component.form.setValue({ password: "short" });
    component.submit();

    if ((api.resetPassword as jest.Mock).mock.calls.length !== 0) {
      throw new Error("API should not be called with invalid form");
    }
    if (component.done()) {
      throw new Error("done() should remain false when form is invalid");
    }
  });

  it("shows success state after successful password reset", () => {
    const { component } = createComponent("valid-token-abc");

    component.form.setValue({ password: "NewPassword99!" });
    component.submit();

    if (!component.done()) {
      throw new Error("done() should be true after successful reset");
    }
    if (component.loading()) {
      throw new Error("loading() should be false after API completes");
    }
    if (component.tokenError() !== null) {
      throw new Error("tokenError() should be null on success");
    }
  });

  it("shows error message when API returns an error with a message", () => {
    const apiError = { error: { message: "Reset token expired" } };
    const api = buildApi({
      resetPassword: jest.fn().mockReturnValue(throwError(() => apiError))
    });
    const { component } = createComponent("expired-token-abc", api);

    component.form.setValue({ password: "NewPassword99!" });
    component.submit();

    if (component.done()) {
      throw new Error("done() should remain false on error");
    }
    if (component.tokenError() !== "Reset token expired") {
      throw new Error(`Expected 'Reset token expired' but got '${component.tokenError()}'`);
    }
    if (component.loading()) {
      throw new Error("loading() should be false after error");
    }
  });

  it("shows fallback message when API error has no message body", () => {
    const api = buildApi({
      resetPassword: jest.fn().mockReturnValue(throwError(() => ({})))
    });
    const { component } = createComponent("bad-token", api);

    component.form.setValue({ password: "NewPassword99!" });
    component.submit();

    const errorMsg = component.tokenError();
    if (!errorMsg || !errorMsg.includes("invalid or has expired")) {
      throw new Error(`Expected fallback error message, got '${errorMsg}'`);
    }
  });

  it("passes the token from query params and the new password to the API", () => {
    const resetPassword = jest.fn().mockReturnValue(of(undefined));
    const { component } = createComponent("my-reset-token", buildApi({ resetPassword }));

    component.form.setValue({ password: "NewPassword99!" });
    component.submit();

    if (resetPassword.mock.calls.length !== 1) {
      throw new Error("API should be called exactly once");
    }
    const [tokenArg, passwordArg] = resetPassword.mock.calls[0];
    if (tokenArg !== "my-reset-token") {
      throw new Error(`Expected token 'my-reset-token' but got '${tokenArg}'`);
    }
    if (passwordArg !== "NewPassword99!") {
      throw new Error(`Expected password 'NewPassword99!' but got '${passwordArg}'`);
    }
  });
});
