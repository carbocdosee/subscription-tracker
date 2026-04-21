import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from "@angular/core";
import { RouterModule } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { ProgressSpinnerModule } from "primeng/progressspinner";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { AuthSessionService } from "../../core/services/auth-session.service";
import { I18nService } from "../../core/services/i18n.service";
import { BillingPlan, PlanTier } from "../../shared/models";

@Component({
  standalone: true,
  selector: "app-upgrade-page",
  imports: [RouterModule, ButtonModule, ProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="plans-page">
      <div class="plans-header">
        <h1 class="plans-title">{{ i18n.t().plansPageTitle }}</h1>
        <p class="plans-subtitle">{{ i18n.t().plansPageSubtitle }}</p>
      </div>

      @if (loading()) {
        <div class="plans-loading">
          <p-progressSpinner strokeWidth="4" [style]="{ width: '40px', height: '40px' }" />
        </div>
      } @else {
        <div class="plans-grid">
          @for (plan of plans(); track plan.id) {
            <div class="plan-card" [class.plan-card--current]="plan.isCurrent" [class.plan-card--pro]="plan.id === 'PRO'">
              @if (plan.id === 'PRO') {
                <div class="plan-badge">{{ i18n.t().planBadgePopular }}</div>
              }
              <div class="plan-name">{{ planLabel(plan.id) }}</div>
              <div class="plan-price">
                @if (plan.priceMonthly === 0) {
                  <span class="plan-price__amount">{{ i18n.t().planFree0 }}</span>
                } @else {
                  <span class="plan-price__amount">\${{ plan.priceMonthly }}</span>
                  <span class="plan-price__period">{{ i18n.t().planMonthly }}</span>
                }
              </div>
              <ul class="plan-features">
                <li>
                  @if (plan.limits.subscriptions === -1) {
                    {{ i18n.t().planUnlimited }}
                  } @else {
                    {{ i18n.t().planUpTo.replace("{0}", plan.limits.subscriptions + " " + i18n.t().planLimitSubscriptions) }}
                  }
                </li>
                <li>
                  @if (plan.limits.teamMembers === -1) {
                    {{ i18n.t().planUnlimited }} {{ i18n.t().planFeatureTeam }}
                  } @else {
                    {{ i18n.t().planUpTo.replace("{0}", plan.limits.teamMembers + " " + i18n.t().planLimitTeam) }}
                  }
                </li>
                @if (plan.id === 'FREE') {
                  <li>✓ {{ i18n.t().planFeatureBasicTracking }}</li>
                  <li>✓ {{ i18n.t().planFeatureManualPayments }}</li>
                  <li>✓ {{ i18n.t().planFeatureBasicAlerts }}</li>
                }
                @if (plan.id !== 'FREE') {
                  <li>✓ {{ i18n.t().planFeatureAnalytics }}</li>
                  <li>✓ {{ i18n.t().planFeatureEmailAlerts }}</li>
                  <li>✓ {{ i18n.t().planFeatureExport }}</li>
                  <li>✓ {{ i18n.t().planFeatureVendorSuggest }}</li>
                }
                @if (plan.id === 'ENTERPRISE') {
                  <li>✓ {{ i18n.t().planFeaturePrioritySupport }}</li>
                  <li>✓ {{ i18n.t().planFeatureCustomIntegrations }}</li>
                }
              </ul>
              <div class="plan-action">
                @if (plan.isCurrent) {
                  <p-button
                    [label]="i18n.t().planCurrentLabel"
                    styleClass="p-button-outlined"
                    [disabled]="true"
                  />
                } @else if (plan.id === 'ENTERPRISE') {
                  <p-button
                    [label]="i18n.t().planContactSales"
                    styleClass="p-button-outlined"
                    (onClick)="contactSales()"
                  />
                } @else if (plan.id !== 'FREE') {
                  <p-button
                    [label]="i18n.t().planGetBtn.replace('{0}', planLabel(plan.id))"
                    [loading]="checkoutLoading() === plan.id"
                    (onClick)="startCheckout(plan.id)"
                  />
                }
              </div>
            </div>
          }
        </div>
      }

      @if (errorMsg()) {
        <p class="plans-error">{{ errorMsg() }}</p>
      }

      <p class="gdpr-notice">
        {{ i18n.t().planGdprNotice }}
        <a routerLink="/account" class="gdpr-link">{{ i18n.t().planGdprManageAccount }}</a>
        {{ i18n.t().planGdprAvailableAnyPlan }}
      </p>
    </div>
  `,
  styles: [`
    .plans-page {
      max-width: 1100px;
      margin: 0 auto;
      padding: 2rem 1rem;
    }
    .plans-header {
      text-align: center;
      margin-bottom: 2.5rem;
    }
    .plans-title { font-size: 2rem; font-weight: 700; margin: 0 0 0.5rem; }
    .plans-subtitle { color: var(--text-color-secondary); margin: 0; }
    .plans-loading {
      display: flex;
      justify-content: center;
      padding: 4rem;
    }
    .plans-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.5rem;
    }
    .plan-card {
      position: relative;
      background: var(--surface-card);
      border: 2px solid var(--surface-border);
      border-radius: 12px;
      padding: 2rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .plan-card--current { border-color: var(--green-500); }
    .plan-card--pro { border-color: var(--primary-color); }
    .plan-badge {
      position: absolute;
      top: -12px;
      left: 50%;
      transform: translateX(-50%);
      background: var(--primary-color);
      color: #fff;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 0.25rem 0.75rem;
      border-radius: 99px;
      white-space: nowrap;
    }
    .plan-name { font-size: 1.25rem; font-weight: 700; }
    .plan-price { display: flex; align-items: baseline; gap: 0.25rem; }
    .plan-price__amount { font-size: 1.75rem; font-weight: 700; }
    .plan-price__period { color: var(--text-color-secondary); font-size: 0.9rem; }
    .plan-features {
      list-style: none;
      padding: 0;
      margin: 0;
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      font-size: 0.9rem;
      color: var(--text-color-secondary);
    }
    .plan-action { margin-top: auto; }
    .plan-action p-button { width: 100%; }
    .plans-error { color: var(--red-500); text-align: center; margin-top: 1rem; }
    .gdpr-notice {
      text-align: center;
      margin-top: 2rem;
      font-size: 0.85rem;
      color: var(--text-color-secondary);
    }
    .gdpr-link { color: var(--primary-color); text-decoration: underline; }
  `]
})
export class UpgradePageComponent implements OnInit {
  private readonly api = inject(TrackerApiService);
  private readonly session = inject(AuthSessionService);
  protected readonly i18n = inject(I18nService);

  readonly loading = signal(true);
  readonly plans = signal<BillingPlan[]>([]);
  readonly checkoutLoading = signal<PlanTier | null>(null);
  readonly errorMsg = signal<string | null>(null);

  ngOnInit(): void {
    this.api.getBillingPlans().subscribe({
      next: (data) => {
        this.plans.set(data.plans);
        this.session.planTier.set(data.currentPlan as PlanTier);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMsg.set(this.i18n.t().planCouldNotLoad);
      }
    });
  }

  protected planLabel(id: PlanTier): string {
    const t = this.i18n.t();
    if (id === "FREE") return t.planFree;
    if (id === "PRO") return t.planPro;
    return t.planEnterprise;
  }

  protected startCheckout(planId: PlanTier): void {
    if (planId === "FREE" || planId === "ENTERPRISE") return;
    this.checkoutLoading.set(planId);
    this.errorMsg.set(null);
    this.api.createCheckoutSession(planId).subscribe({
      next: ({ checkoutUrl }) => {
        window.location.href = checkoutUrl;
      },
      error: () => {
        this.checkoutLoading.set(null);
        this.errorMsg.set("Failed to start checkout. Please try again.");
      }
    });
  }

  protected contactSales(): void {
    window.open("mailto:sales@example.com?subject=Enterprise%20Plan", "_blank");
  }
}
