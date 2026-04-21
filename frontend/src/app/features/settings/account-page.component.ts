import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { LowerCasePipe, NgIf } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AuthSessionService } from "../../core/services/auth-session.service";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  standalone: true,
  selector: "app-account-page",
  imports: [NgIf, RouterLink, LowerCasePipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="account-container">
      <div class="card account-card">
        <h2 class="page-title">{{ i18n.t().accountTitle }}</h2>

        <!-- User identity card -->
        <div class="user-info-row">
          <div class="user-avatar">{{ userInitial() }}</div>
          <div class="user-meta">
            <span class="user-email">{{ userEmail() }}</span>
            <span class="user-role-badge" [class]="'role-' + (userRole() | lowercase)">{{ userRole() }}</span>
          </div>
        </div>

        <section class="account-section" *ngIf="isAdmin()">
          <h3 class="section-title">{{ i18n.t().billingTitle }}</h3>

          <ng-container *ngIf="session.planTier() === 'PRO' || session.planTier() === 'ENTERPRISE'; else freePlan">
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
          <ng-template #freePlan>
            <p class="section-subtitle muted">{{ i18n.t().freePlanBillingMsg }}</p>
            <a class="btn-primary" routerLink="/billing/plans">{{ i18n.t().upgradeLink }}</a>
          </ng-template>
        </section>

        <section class="account-section" *ngIf="!isAdmin()">
          <p class="muted">{{ i18n.t().billingAdminOnly }}</p>
        </section>

        <!-- Notifications / Digest Settings -->
        <section class="account-section" *ngIf="isAdmin()">
          <h3 class="section-title">{{ i18n.t().digestSectionTitle }}</h3>
          <div class="digest-row">
            <label class="toggle-wrap">
              <input type="checkbox" class="toggle-checkbox" [(ngModel)]="digestEnabled" (ngModelChange)="onDigestToggle()" />
              <span class="toggle-track">
                <span class="toggle-thumb"></span>
              </span>
            </label>
            <div class="digest-labels">
              <span class="digest-label">{{ i18n.t().digestEnabledLabel }}</span>
              <span class="digest-hint">{{ i18n.t().digestEnabledHint }}</span>
            </div>
          </div>
          <p class="success-text" *ngIf="digestSaved()">{{ i18n.t().digestSaved }}</p>
          <p class="error-text" *ngIf="digestError()">{{ digestError() }}</p>
        </section>

        <section class="account-section">
          <h3 class="section-title">{{ i18n.t().personalDataTitle }}</h3>
          <p class="section-subtitle">{{ i18n.t().personalDataSubtitle }}</p>

          <button
            class="btn-secondary"
            [disabled]="exportLoading()"
            (click)="exportData()"
          >
            {{ exportLoading() ? i18n.t().exportingData : i18n.t().exportMyData }}
          </button>
          <p *ngIf="exportError()" class="error-text">{{ exportError() }}</p>

          <div class="danger-zone">
            <h4 class="danger-title">{{ i18n.t().deleteAccountTitle }}</h4>
            <p class="section-subtitle">{{ i18n.t().deleteAccountSubtitle }}</p>
            <ng-container *ngIf="!confirmDelete(); else confirmDeleteTpl">
              <button class="btn-danger" (click)="confirmDelete.set(true)">
                {{ i18n.t().deleteAccountBtn }}
              </button>
            </ng-container>
            <ng-template #confirmDeleteTpl>
              <p class="confirm-msg">{{ i18n.t().deleteAccountConfirmMsg }}</p>
              <div class="confirm-actions">
                <button class="btn-danger" [disabled]="deleteLoading()" (click)="deleteAccount()">
                  {{ deleteLoading() ? i18n.t().deletingAccount : i18n.t().deleteAccountConfirmYes }}
                </button>
                <button class="btn-ghost" [disabled]="deleteLoading()" (click)="confirmDelete.set(false)">
                  {{ i18n.t().msgCancelLabel }}
                </button>
              </div>
            </ng-template>
            <p *ngIf="deleteError()" class="error-text">{{ deleteError() }}</p>
          </div>
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
    .user-info-row {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      margin-top: 1rem;
    }
    .user-avatar {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: linear-gradient(135deg, #0b61d8 0%, #0ea5e9 100%);
      color: #fff;
      font-size: 1.15rem;
      font-weight: 700;
      display: grid;
      place-items: center;
      flex-shrink: 0;
    }
    .user-meta {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 0;
    }
    .user-email {
      font-size: 0.92rem;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .user-role-badge {
      display: inline-block;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      padding: 2px 8px;
      border-radius: 999px;
      width: fit-content;
    }
    .role-admin { background: #fef3c7; color: #92400e; }
    .role-editor { background: #dbeafe; color: #1e40af; }
    .role-viewer { background: #f1f5f9; color: #475569; }
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
    .btn-primary {
      background: #0b61d8;
      color: #fff;
      border: none;
      padding: 0.5rem 1.25rem;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.9rem;
      font-weight: 500;
    }
    .btn-primary:hover:not(:disabled) { background: #0950b8; }
    .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
    .btn-secondary {
      background: #f1f5f9;
      color: #0f172a;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 1.25rem;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.9rem;
      font-weight: 500;
    }
    .btn-secondary:hover:not(:disabled) { background: #e2e8f0; }
    .btn-secondary:disabled { opacity: 0.6; cursor: not-allowed; }
    .danger-zone {
      margin-top: 1.5rem;
      padding: 1rem;
      border: 1px solid #fecaca;
      border-radius: 8px;
      background: #fff5f5;
    }
    .danger-title {
      font-size: 0.95rem;
      font-weight: 600;
      color: #b91c1c;
      margin: 0 0 0.4rem;
    }
    .btn-danger {
      background: #ef4444;
      color: #fff;
      border: none;
      padding: 0.5rem 1.25rem;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.9rem;
      font-weight: 500;
    }
    .btn-danger:hover:not(:disabled) { background: #dc2626; }
    .btn-danger:disabled { opacity: 0.6; cursor: not-allowed; }
    .btn-ghost {
      background: transparent;
      border: 1px solid #94a3b8;
      color: #475569;
      padding: 0.5rem 1.25rem;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.9rem;
    }
    .btn-ghost:disabled { opacity: 0.6; cursor: not-allowed; }
    .confirm-msg {
      font-size: 0.9rem;
      color: #7f1d1d;
      margin: 0 0 0.75rem;
    }
    .confirm-actions {
      display: flex;
      gap: 0.75rem;
      align-items: center;
    }
    .digest-row {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 0.5rem;
    }
    .toggle-wrap {
      display: flex;
      align-items: center;
      flex-shrink: 0;
      margin-top: 2px;
      cursor: pointer;
    }
    .toggle-checkbox {
      display: none;
    }
    .toggle-track {
      width: 40px;
      height: 22px;
      border-radius: 999px;
      background: #cbd5e1;
      display: flex;
      align-items: center;
      padding: 2px;
      transition: background 0.2s;
    }
    .toggle-checkbox:checked + .toggle-track {
      background: #6366f1;
    }
    .toggle-thumb {
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: #fff;
      transition: transform 0.2s;
      box-shadow: 0 1px 3px rgba(0,0,0,0.15);
    }
    .toggle-checkbox:checked ~ .toggle-track .toggle-thumb {
      transform: translateX(18px);
    }
    .digest-labels {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }
    .digest-label {
      font-size: 0.9rem;
      font-weight: 600;
      color: #0f172a;
    }
    .digest-hint {
      font-size: 0.82rem;
      color: #64748b;
      line-height: 1.4;
    }
    .success-text {
      color: #16a34a;
      font-size: 0.9rem;
      margin-top: 0.4rem;
    }
  `]
})
export class AccountPageComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly session = inject(AuthSessionService);
  private readonly api = inject(TrackerApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = () => this.session.currentUserRole() === "ADMIN";
  readonly userEmail = computed(() => this.session.currentUserEmail() ?? "—");
  readonly userRole = computed(() => this.session.currentUserRole() ?? "—");
  readonly userInitial = computed(() => {
    const email = this.session.currentUserEmail();
    return email ? email[0].toUpperCase() : "?";
  });

  readonly portalLoading = signal(false);
  readonly portalError = signal<string | null>(null);
  readonly exportLoading = signal(false);
  readonly exportError = signal<string | null>(null);
  readonly confirmDelete = signal(false);
  readonly deleteLoading = signal(false);
  readonly deleteError = signal<string | null>(null);

  digestEnabled = true;
  readonly digestSaved = signal(false);
  readonly digestError = signal<string | null>(null);
  private digestSavedTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    if (this.isAdmin()) {
      this.api.getDigestSettings()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (s) => { this.digestEnabled = s.weeklyDigestEnabled; },
          error: () => {}
        });
    }
  }

  onDigestToggle(): void {
    this.digestSaved.set(false);
    this.digestError.set(null);
    this.api.updateDigestSettings({ weeklyDigestEnabled: this.digestEnabled })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.digestSaved.set(true);
          if (this.digestSavedTimer) clearTimeout(this.digestSavedTimer);
          this.digestSavedTimer = setTimeout(() => this.digestSaved.set(false), 3000);
        },
        error: () => this.digestError.set(this.i18n.t().digestSaveError)
      });
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

  exportData(): void {
    this.exportLoading.set(true);
    this.exportError.set(null);
    this.api.exportPersonalData()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.exportLoading.set(false);
          const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = "my-data-export.json";
          a.click();
          URL.revokeObjectURL(url);
        },
        error: () => {
          this.exportLoading.set(false);
          this.exportError.set(this.i18n.t().exportDataError);
        }
      });
  }

  deleteAccount(): void {
    this.deleteLoading.set(true);
    this.deleteError.set(null);
    this.api.deleteAccount()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.session.logout();
          void this.router.navigateByUrl("/auth");
        },
        error: () => {
          this.deleteLoading.set(false);
          this.deleteError.set(this.i18n.t().deleteAccountError);
        }
      });
  }
}
