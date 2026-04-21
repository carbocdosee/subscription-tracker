import { TestBed } from "@angular/core/testing";
import { provideRouter } from "@angular/router";
import { Subject, of, throwError } from "rxjs";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ForgotPasswordPageComponent } from "./forgot-password-page.component";
import { TrackerApiService } from "../../core/services/tracker-api.service";

function buildApi(overrides: Partial<{ forgotPassword: jest.Mock }>): Partial<TrackerApiService> {
  return {
    forgotPassword: jest.fn().mockReturnValue(of({ message: "ok" })),
    ...overrides
  };
}

function createComponent(api: Partial<TrackerApiService> = buildApi({})) {
  TestBed.configureTestingModule({
    imports: [ForgotPasswordPageComponent, NoopAnimationsModule],
    providers: [provideRouter([]), { provide: TrackerApiService, useValue: api }]
  });
  const fixture = TestBed.createComponent(ForgotPasswordPageComponent);
  fixture.detectChanges();
  return fixture.componentInstance;
}

describe("ForgotPasswordPageComponent", () => {
  afterEach(() => TestBed.resetTestingModule());

  it("does not call API and does not show success when form is empty", () => {
    const api = buildApi({});
    const component = createComponent(api);

    component.submit();

    if ((api.forgotPassword as jest.Mock).mock.calls.length !== 0) {
      throw new Error("API should not be called with invalid form");
    }
    if (component.sent()) {
      throw new Error("sent() should remain false when form is invalid");
    }
  });

  it("shows success state after API call succeeds", () => {
    const api = buildApi({ forgotPassword: jest.fn().mockReturnValue(of({ message: "ok" })) });
    const component = createComponent(api);

    component.form.setValue({ email: "user@example.com" });
    component.submit();

    if (!component.sent()) {
      throw new Error("sent() should be true after successful API call");
    }
    if (component.loading()) {
      throw new Error("loading() should be false after API completes");
    }
  });

  it("shows success state even when API returns an error (prevents email enumeration)", () => {
    const api = buildApi({
      forgotPassword: jest.fn().mockReturnValue(throwError(() => ({ status: 500, error: {} })))
    });
    const component = createComponent(api);

    component.form.setValue({ email: "user@example.com" });
    component.submit();

    if (!component.sent()) {
      throw new Error("sent() should be true even on API error to prevent email enumeration");
    }
    if (component.loading()) {
      throw new Error("loading() should be false after API error");
    }
  });

  it("sets loading to true while request is in flight", () => {
    const subject = new Subject<{ message: string }>();
    const api = buildApi({ forgotPassword: jest.fn().mockReturnValue(subject) });
    const component = createComponent(api);

    component.form.setValue({ email: "user@example.com" });
    component.submit();

    if (!component.loading()) {
      throw new Error("loading() should be true while request is in flight");
    }

    subject.next({ message: "ok" });
    subject.complete();

    if (component.loading()) {
      throw new Error("loading() should be false after request completes");
    }
  });

  it("passes the form email to the API", () => {
    const forgotPassword = jest.fn().mockReturnValue(of({ message: "ok" }));
    const component = createComponent(buildApi({ forgotPassword }));

    component.form.setValue({ email: "specific@example.com" });
    component.submit();

    if (forgotPassword.mock.calls.length !== 1) {
      throw new Error("API should be called exactly once");
    }
    if (forgotPassword.mock.calls[0][0] !== "specific@example.com") {
      throw new Error(`Expected 'specific@example.com' but got '${forgotPassword.mock.calls[0][0]}'`);
    }
  });
});
