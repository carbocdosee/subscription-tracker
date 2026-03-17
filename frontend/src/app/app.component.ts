import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from "@angular/core";
import { DatePipe, NgClass, NgFor, NgIf } from "@angular/common";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { ToastModule } from "primeng/toast";
import { filter, timer } from "rxjs";
import { AuthSessionService } from "./core/services/auth-session.service";
import { TrackerApiService } from "./core/services/tracker-api.service";
import { I18nService } from "./core/services/i18n.service";
import { NotificationItem } from "./shared/models";
import { OnboardingWizardComponent } from "./features/onboarding/onboarding-wizard.component";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIf, NgFor, NgClass, DatePipe, ButtonModule, ToastModule, OnboardingWizardComponent],
  template: `
    <p-toast position="top-right" />
    <app-onboarding-wizard *ngIf="showOnboarding()" (wizardCompleted)="onOnboardingCompleted()" />
    <div class="app-shell" (click)="closeOverlays()">
      <ng-container *ngIf="showAppShell(); else authOutlet">
        <header class="fade-in card app-header">
          <div class="brand">
            <div class="brand-mark">ST</div>
            <div>
              <h1 class="title">Subscription Tracker</h1>
              <p class="subtitle">{{ i18n.t().appSubtitle }}</p>
            </div>
          </div>
          <div class="header-actions">

            <!-- Language picker -->
            <div class="lang-shell">
              <button
                type="button"
                class="lang-btn"
                (click)="toggleLangMenu($event)"
                [attr.aria-expanded]="langMenuOpen()"
              >
                <span class="lang-flag">{{ i18n.currentFlag() }}</span>
                <span class="lang-code">{{ i18n.lang().toUpperCase() }}</span>
                <i class="pi pi-angle-down lang-chevron" [class.open]="langMenuOpen()"></i>
              </button>
              <div class="lang-panel card" *ngIf="langMenuOpen()" (click)="$event.stopPropagation()">
                <button
                  *ngFor="let opt of i18n.langOptions"
                  type="button"
                  class="lang-option"
                  [class.lang-option--active]="i18n.lang() === opt.code"
                  (click)="selectLang(opt.code)"
                >
                  <span class="lang-option-flag">{{ opt.flag }}</span>
                  <span>{{ opt.name }}</span>
                </button>
              </div>
            </div>

            <!-- Notifications -->
            <div class="notification-shell">
              <button
                pButton
                type="button"
                class="p-button-sm p-button-outlined notification-button"
                icon="pi pi-bell"
                (click)="toggleNotifications($event)"
              ></button>
              <span class="notification-badge" *ngIf="notificationsUnreadCount() > 0">{{ unreadBadge() }}</span>

              <section class="card notification-panel" *ngIf="notificationsOpen()" (click)="$event.stopPropagation()">
                <div class="notification-head">
                  <strong>{{ i18n.t().notifications }}</strong>
                  <button
                    pButton
                    type="button"
                    class="p-button-sm p-button-text"
                    [label]="i18n.t().markAllRead"
                    [disabled]="notificationsUnreadCount() === 0"
                    (click)="markAllNotificationsRead($event)"
                  ></button>
                </div>

                <div class="notification-state" *ngIf="notificationsLoading()">{{ i18n.t().loadingNotifications }}</div>
                <div class="notification-state notification-state--error" *ngIf="notificationsError()">{{ notificationsError() }}</div>

                <ul class="notification-list" *ngIf="!notificationsLoading() && notifications().length > 0">
                  <li
                    *ngFor="let item of notifications()"
                    [class.unread]="!item.read"
                    (click)="openNotification(item, $event)"
                  >
                    <span class="notification-dot" [ngClass]="severityClass(item.severity)"></span>
                    <div class="notification-body">
                      <p class="notification-title">{{ item.title }}</p>
                      <p class="notification-message">{{ item.message }}</p>
                      <small>{{ item.createdAt | date: "yyyy-MM-dd HH:mm" }}</small>
                    </div>
                    <button
                      *ngIf="!item.read"
                      pButton
                      type="button"
                      class="p-button-sm p-button-text notification-read-btn"
                      icon="pi pi-check"
                      (click)="markNotificationRead(item, $event)"
                    ></button>
                  </li>
                </ul>

                <p class="notification-empty" *ngIf="!notificationsLoading() && notifications().length === 0">
                  {{ i18n.t().noNotifications }}
                </p>
              </section>
            </div>

            <button
              pButton
              type="button"
              class="p-button-sm p-button-text"
              icon="pi pi-sign-out"
              [label]="i18n.t().logout"
              (click)="logout()"
            ></button>
          </div>
        </header>

        <nav class="fade-in nav-row">
          <a routerLink="/dashboard" routerLinkActive="active"><i class="pi pi-chart-line"></i> {{ i18n.t().navDashboard }}</a>
          <a routerLink="/subscriptions" routerLinkActive="active"><i class="pi pi-briefcase"></i> {{ i18n.t().navSubscriptions }}</a>
          <a routerLink="/analytics" routerLinkActive="active"><i class="pi pi-chart-bar"></i> {{ i18n.t().navAnalytics }}</a>
          <a routerLink="/team" routerLinkActive="active"><i class="pi pi-users"></i> {{ i18n.t().navTeam }}</a>
          <a *ngIf="isAdmin()" routerLink="/settings/account" routerLinkActive="active"><i class="pi pi-cog"></i> {{ i18n.t().navSettings }}</a>
        </nav>
      </ng-container>

      <ng-template #authOutlet></ng-template>
      <main class="fade-in content">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [
    `
      .app-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .brand-mark {
        height: 42px;
        width: 42px;
        border-radius: 12px;
        display: grid;
        place-items: center;
        font-weight: 700;
        color: #fff;
        background: linear-gradient(135deg, #0b61d8 0%, #0ea5e9 100%);
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 8px;
        position: relative;
      }
      /* ── Language picker ─────────────────────────────────────── */
      .lang-shell {
        position: relative;
      }
      .lang-btn {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        border: 1px solid #c9dcff;
        color: #0c4a6e;
        background: #eff6ff;
        border-radius: 999px;
        padding: 6px 10px;
        font-size: 0.82rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 140ms ease, border-color 140ms ease;
        line-height: 1;
      }
      .lang-btn:hover {
        background: #dbeafe;
        border-color: #93c5fd;
      }
      .lang-flag {
        font-size: 1rem;
        line-height: 1;
      }
      .lang-code {
        letter-spacing: 0.03em;
      }
      .lang-chevron {
        font-size: 0.7rem;
        transition: transform 180ms ease;
      }
      .lang-chevron.open {
        transform: rotate(180deg);
      }
      .lang-panel {
        position: absolute;
        top: calc(100% + 8px);
        right: 0;
        min-width: 140px;
        z-index: 50;
        padding: 6px;
        border-radius: 10px;
        display: grid;
        gap: 2px;
      }
      .lang-option {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px 10px;
        border: none;
        background: transparent;
        border-radius: 8px;
        cursor: pointer;
        font-size: 0.88rem;
        color: #1e293b;
        transition: background 120ms ease;
        width: 100%;
        text-align: left;
      }
      .lang-option:hover {
        background: #f1f5f9;
      }
      .lang-option--active {
        background: #eff6ff;
        color: #0c4a6e;
        font-weight: 600;
      }
      .lang-option-flag {
        font-size: 1.1rem;
        line-height: 1;
      }
      /* ─────────────────────────────────────────────────────────── */
      .title {
        margin: 0;
        font-size: clamp(1.3rem, 2vw, 1.7rem);
      }
      .subtitle {
        margin: 4px 0 0 0;
        color: var(--muted);
      }
      .nav-row {
        margin-top: 14px;
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
      }
      .nav-row a {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        text-decoration: none;
        color: #1e293b;
        border: 1px solid #c3d4ea;
        border-radius: 999px;
        padding: 9px 14px;
        font-weight: 500;
        transition: all 140ms ease;
      }
      .nav-row a.active,
      .nav-row a:hover {
        border-color: #95b8ec;
        color: #0c4a6e;
        background: #e8f2ff;
      }
      .content {
        margin-top: 16px;
      }
      .notification-shell {
        position: relative;
      }
      .notification-button {
        position: relative;
      }
      .notification-badge {
        position: absolute;
        top: -6px;
        right: -6px;
        min-width: 18px;
        height: 18px;
        border-radius: 999px;
        padding: 0 6px;
        background: #dc2626;
        color: #fff;
        font-size: 0.72rem;
        font-weight: 700;
        display: grid;
        place-items: center;
        border: 1px solid #fff;
      }
      .notification-panel {
        position: absolute;
        top: calc(100% + 8px);
        right: 0;
        width: min(430px, calc(100vw - 24px));
        z-index: 50;
        padding: 10px;
        border-radius: 12px;
      }
      .notification-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 10px;
        margin-bottom: 6px;
      }
      .notification-state {
        color: #475569;
        font-size: 0.88rem;
        padding: 8px 4px;
      }
      .notification-state--error {
        color: #b91c1c;
      }
      .notification-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: grid;
        gap: 7px;
        max-height: 360px;
        overflow: auto;
      }
      .notification-list li {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: start;
        gap: 8px;
        border: 1px solid #dce5f3;
        border-radius: 10px;
        padding: 8px 9px;
        background: #fbfdff;
        cursor: pointer;
      }
      .notification-list li.unread {
        border-color: #b8cef2;
        background: #eef5ff;
      }
      .notification-dot {
        width: 8px;
        height: 8px;
        border-radius: 999px;
        margin-top: 6px;
      }
      .notification-dot.INFO {
        background: #0ea5e9;
      }
      .notification-dot.WARNING {
        background: #f59e0b;
      }
      .notification-dot.DANGER {
        background: #ef4444;
      }
      .notification-body {
        min-width: 0;
      }
      .notification-title {
        margin: 0;
        font-size: 0.9rem;
        font-weight: 600;
        color: #0f172a;
      }
      .notification-message {
        margin: 2px 0 4px 0;
        font-size: 0.84rem;
        color: #334155;
      }
      .notification-body small {
        color: #64748b;
      }
      .notification-read-btn {
        margin-top: -2px;
      }
      .notification-empty {
        margin: 10px 2px 4px;
        font-size: 0.88rem;
        color: #64748b;
      }
      @media (max-width: 900px) {
        .app-header {
          flex-direction: column;
          align-items: flex-start;
        }
        .header-actions {
          width: 100%;
          justify-content: flex-end;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  protected readonly i18n = inject(I18nService);
  private readonly session = inject(AuthSessionService);
  private readonly api = inject(TrackerApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly currentUrl = signal(this.router.url);

  readonly notificationsOpen = signal(false);
  readonly langMenuOpen = signal(false);
  readonly notificationsLoading = signal(false);
  readonly notifications = signal<NotificationItem[]>([]);
  readonly notificationsUnreadCount = signal(0);
  readonly notificationsError = signal<string | null>(null);
  readonly unreadBadge = computed(() => {
    const value = this.notificationsUnreadCount();
    return value > 99 ? "99+" : `${value}`;
  });

  readonly showAppShell = computed(
    () => this.session.isAuthenticated() && !this.currentUrl().startsWith("/auth")
  );
  readonly isAdmin = computed(() => this.session.currentUserRole() === "ADMIN");
  readonly showOnboarding = signal(false);

  private onboardingChecked = false;

  constructor() {
    effect(() => {
      const shell = this.showAppShell();
      if (!shell) {
        this.onboardingChecked = false;
        this.showOnboarding.set(false);
        return;
      }
      if (!this.onboardingChecked && this.session.currentUserRole() === "ADMIN") {
        this.onboardingChecked = true;
        this.checkOnboarding();
      }
    });

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((event) => {
        this.currentUrl.set(event.urlAfterRedirects);
        if (this.showAppShell()) {
          this.refreshNotificationCount();
          if (this.notificationsOpen()) {
            this.loadNotifications();
          }
        }
      });

    timer(0, 45_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.showAppShell()) return;
        this.refreshNotificationCount();
        if (this.notificationsOpen()) {
          this.loadNotifications();
        }
      });
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.langMenuOpen.set(false);
    if (this.notificationsOpen()) {
      this.notificationsOpen.set(false);
      return;
    }
    this.notificationsOpen.set(true);
    this.loadNotifications();
  }

  toggleLangMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.notificationsOpen.set(false);
    this.langMenuOpen.update((v) => !v);
  }

  selectLang(code: "en" | "ru"): void {
    this.i18n.setLanguage(code);
    this.langMenuOpen.set(false);
  }

  closeOverlays(): void {
    if (this.notificationsOpen()) this.notificationsOpen.set(false);
    if (this.langMenuOpen()) this.langMenuOpen.set(false);
  }

  openNotification(item: NotificationItem, event: MouseEvent): void {
    event.stopPropagation();
    if (!item.read) {
      this.markReadLocally([item.key]);
      this.api
        .markNotificationsRead([item.key])
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ error: () => this.loadNotifications() });
    }
    if (item.actionPath) {
      this.notificationsOpen.set(false);
      void this.router.navigateByUrl(item.actionPath);
    }
  }

  markNotificationRead(item: NotificationItem, event: MouseEvent): void {
    event.stopPropagation();
    if (item.read) return;
    this.markReadLocally([item.key]);
    this.api
      .markNotificationsRead([item.key])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => this.loadNotifications() });
  }

  markAllNotificationsRead(event: MouseEvent): void {
    event.stopPropagation();
    if (this.notificationsUnreadCount() === 0) return;
    this.api
      .markAllNotificationsRead()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          const allKeys = this.notifications().map((item) => item.key);
          this.markReadLocally(allKeys);
        },
        error: () => this.loadNotifications()
      });
  }

  severityClass(severity: NotificationItem["severity"]): string {
    return severity;
  }

  logout(): void {
    this.session.logout();
  }

  onOnboardingCompleted(): void {
    this.showOnboarding.set(false);
  }

  private checkOnboarding(): void {
    this.api
      .getCompanySettings()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (settings) => {
          if (!settings.onboarding?.completed) {
            this.showOnboarding.set(true);
          }
        }
      });
  }

  private refreshNotificationCount(): void {
    this.api
      .getNotificationsUnreadCount()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.notificationsUnreadCount.set(result.unreadCount)
      });
  }

  private loadNotifications(): void {
    this.notificationsLoading.set(true);
    this.notificationsError.set(null);
    this.api
      .getNotifications(25)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (feed) => {
          this.notifications.set(feed.items);
          this.notificationsUnreadCount.set(feed.unreadCount);
          this.notificationsLoading.set(false);
        },
        error: () => {
          this.notificationsLoading.set(false);
          this.notificationsError.set(this.i18n.t().notificationsError);
        }
      });
  }

  private markReadLocally(keys: string[]): void {
    const keySet = new Set(keys);
    let reducedUnread = 0;
    const updated = this.notifications().map((item) => {
      if (!item.read && keySet.has(item.key)) {
        reducedUnread += 1;
        return { ...item, read: true };
      }
      return item;
    });
    this.notifications.set(updated);
    this.notificationsUnreadCount.set(Math.max(this.notificationsUnreadCount() - reducedUnread, 0));
  }
}
