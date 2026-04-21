import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { InactivityService } from "./inactivity.service";
import { AuthSessionService } from "./auth-session.service";
import { TrackerApiService } from "./tracker-api.service";

const INACTIVITY_MS = 30 * 60 * 1000;

class MockAuthSession {
  private _authenticated = true;
  readonly logout = jest.fn();
  isAuthenticated = () => this._authenticated;
  setAuthenticated(value: boolean) { this._authenticated = value; }
}

class MockApi {
  readonly logout = jest.fn().mockReturnValue(of(undefined));
}

function createService() {
  const session = new MockAuthSession();
  const api = new MockApi();

  TestBed.configureTestingModule({
    providers: [
      InactivityService,
      { provide: AuthSessionService, useValue: session },
      { provide: TrackerApiService, useValue: api }
    ]
  });

  const service = TestBed.inject(InactivityService);
  return { service, session, api };
}

describe("InactivityService", () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => {
    jest.useRealTimers();
    TestBed.resetTestingModule();
  });

  it("does not log out before the inactivity timeout elapses", () => {
    const { service, api } = createService();
    service.start();

    jest.advanceTimersByTime(INACTIVITY_MS - 1);

    if (api.logout.mock.calls.length !== 0) {
      throw new Error("Logout should not be called before the timeout expires");
    }
  });

  it("logs out user after the inactivity timeout", () => {
    const { service, api, session } = createService();
    service.start();

    jest.advanceTimersByTime(INACTIVITY_MS);

    if (api.logout.mock.calls.length !== 1) {
      throw new Error(`Expected API logout to be called once, called ${api.logout.mock.calls.length} times`);
    }
    if (session.logout.mock.calls.length !== 1) {
      throw new Error(`Expected session logout to be called once, called ${session.logout.mock.calls.length} times`);
    }
  });

  it("resets the inactivity timer when user activity is detected", () => {
    const { service, api } = createService();
    service.start();

    // Advance close to the timeout, then fire an activity event
    jest.advanceTimersByTime(INACTIVITY_MS - 1000);
    document.dispatchEvent(new Event("mousemove"));

    // Advance past the original timeout — timer should have been reset
    jest.advanceTimersByTime(INACTIVITY_MS - 1000);

    if (api.logout.mock.calls.length !== 0) {
      throw new Error("Logout should not fire after a timer reset from user activity");
    }
  });

  it("logs out after reset timer also expires", () => {
    const { service, api } = createService();
    service.start();

    jest.advanceTimersByTime(INACTIVITY_MS - 1000);
    document.dispatchEvent(new Event("click"));

    // Full 30 minutes past the reset activity event
    jest.advanceTimersByTime(INACTIVITY_MS + 400); // +400ms for debounce

    if (api.logout.mock.calls.length !== 1) {
      throw new Error("Logout should fire after reset timer expires");
    }
  });

  it("does not log out if user is already unauthenticated when timeout fires", () => {
    const { service, api, session } = createService();
    session.setAuthenticated(false);
    service.start();

    jest.advanceTimersByTime(INACTIVITY_MS);

    if (api.logout.mock.calls.length !== 0) {
      throw new Error("Logout API should not be called when user is not authenticated");
    }
  });

  it("calling start() twice is a no-op — does not double-register activity listeners", () => {
    const { service, api } = createService();
    service.start();
    service.start(); // second call should be ignored

    jest.advanceTimersByTime(INACTIVITY_MS);

    // Logout should still only be called once
    if (api.logout.mock.calls.length !== 1) {
      throw new Error(`Expected 1 logout call, got ${api.logout.mock.calls.length}`);
    }
  });
});
