import { Injectable, OnDestroy, inject } from "@angular/core";
import { fromEvent, merge, Subscription } from "rxjs";
import { debounceTime } from "rxjs/operators";
import { AuthSessionService } from "./auth-session.service";
import { TrackerApiService } from "./tracker-api.service";

/** Logs the user out after this many milliseconds of inactivity. */
const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

const ACTIVITY_EVENTS = ["mousemove", "keydown", "click", "touchstart", "scroll"] as const;

@Injectable({ providedIn: "root" })
export class InactivityService implements OnDestroy {
  private readonly session = inject(AuthSessionService);
  private readonly api = inject(TrackerApiService);

  private timer: ReturnType<typeof setTimeout> | null = null;
  private activitySub: Subscription | null = null;

  /** Call once from AppComponent to start monitoring. */
  start(): void {
    if (this.activitySub) return; // already started

    const activity$ = merge(...ACTIVITY_EVENTS.map((evt) => fromEvent(document, evt))).pipe(
      debounceTime(300) // coalesce rapid events
    );

    this.activitySub = activity$.subscribe(() => this.resetTimer());
    this.resetTimer();
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private resetTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
    }
    this.timer = setTimeout(() => this.onInactive(), INACTIVITY_TIMEOUT_MS);
  }

  private onInactive(): void {
    if (!this.session.isAuthenticated()) return;
    this.api.logout().subscribe({ complete: () => this.session.logout() });
    this.stop();
  }

  private stop(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.activitySub?.unsubscribe();
    this.activitySub = null;
  }
}
