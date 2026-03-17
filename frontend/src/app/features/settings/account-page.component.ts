import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { NgIf } from "@angular/common";
import { RouterLink } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AuthSessionService } from "../../core/services/auth-session.service";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  standalone: true,
  selector: "app-account-page",
  imports: [NgIf, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="account-container">
      <div class="card account-card">
        <h2 class="page-title">{{ i18n.t().accountTitle }}</h2>

        <section class="account-section" *ngIf="isAdmin()">
          <h3 class="section-title">{{ i18n.t().billingTitle }}</h3>

          <ng-container *ngIf="statusLoaded()">
            <ng-container *ngIf="subscriptionStatus() === 'ACTIVE'; else notActive">
              <p class="section-subtitle">{{ i18n.t().manageBillingSubtitle }}</p>
              <button
                class="btn-primary"
                [disabled]="portalLoading()"
                (click)="openPortal()"
              >
                {{ portalLoading() ? i18n.t().redirecting : i18n.t().manageBilling }}
              </button>
              <p *ngIf="portalError()" class="error-text">{{ portalError() }}</p>
            </ng-container>
            <ng-template #notActive>
              <p class="section-subtitle muted">
                {{ i18n.t().noActiveSubscriptionMsg }}
                <a routerLink="/billing/upgrade">{{ i18n.t().upgradeLink }}</a> to access billing management.
              </p>
            </ng-template>
          </ng-container>

          <p *ngIf="!statusLoaded() && !statusError()" class="muted">{{ i18n.t().loadingBillingStatus }}</p>
          <p *ngIf="statusError()" class="error-text">{{ statusError() }}</p>
        </section>

        <section class="account-section" *ngIf="!isAdmin()">
          <p class="muted">{{ i18n.t().billingAdminOnly }}</p>
        </section>
      </div>
    </div>
  `,
  styles: [`
    .account-container {
      max-width: 560px;
      margin: 0 auto;
    }
    .account-card {
      padding: 1.5rem;
    }
    .account-section {
      margin-top: 1.5rem;
    }
    .section-title {
      font-size: 1rem;
      font-weight: 600;
      margin: 0 0 0.5rem;
      color: #0f172a;
    }
    .section-subtitle {
      font-size: 0.9rem;
      color: #475569;
      margin: 0 0 1rem;
    }
    .muted {
      color: #64748b;
      font-size: 0.9rem;
    }
    .error-text {
      color: var(--red-500, #ef4444);
      font-size: 0.9rem;
      margin-top: 0.5rem;
    }
  `]
})
export class AccountPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly session = inject(AuthSessionService);
  private readonly api = inject(TrackerApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = () => this.session.currentUserRole() === "ADMIN";
  readonly subscriptionStatus = signal<string | null>(null);
  readonly statusLoaded = signal(false);
  readonly statusError = signal<string | null>(null);
  readonly portalLoading = signal(false);
  readonly portalError = signal<string | null>(null);

  constructor() {
    if (this.isAdmin()) {
      this.api.getBillingStatus()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: ({ subscriptionStatus }) => {
            this.subscriptionStatus.set(subscriptionStatus);
            this.statusLoaded.set(true);
          },
          error: () => {
            this.statusError.set(this.i18n.t().couldNotLoadBilling);
            this.statusLoaded.set(true);
          }
        });
    }
  }

  openPortal(): void {
    this.portalLoading.set(true);
    this.portalError.set(null);
    this.api.getBillingPortal()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ portalUrl }) => {
          this.portalLoading.set(false);
          window.open(portalUrl, "_blank");
        },
        error: () => {
          this.portalLoading.set(false);
          this.portalError.set(this.i18n.t().failedToOpenPortal);
        }
      });
  }
}
