import { ChangeDetectionStrategy, Component, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { TrackerApiService } from "../../core/services/tracker-api.service";

@Component({
  standalone: true,
  selector: "app-upgrade-page",
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="upgrade-container">
      <div class="card upgrade-card">
        <h2 class="page-title">Your trial has expired</h2>
        <p class="section-subtitle">
          Activate a subscription to continue managing your SaaS tools.
        </p>

        <div class="plan-grid">
          <article
            class="plan-card"
            [class.plan-card--selected]="selectedPlan() === 'monthly'"
            (click)="selectedPlan.set('monthly')"
          >
            <p class="plan-name">Monthly</p>
            <p class="plan-desc">Billed every month. Cancel any time.</p>
          </article>

          <article
            class="plan-card"
            [class.plan-card--selected]="selectedPlan() === 'annual'"
            (click)="selectedPlan.set('annual')"
          >
            <p class="plan-name">Annual</p>
            <p class="plan-desc">Billed once per year. Save 20%.</p>
          </article>
        </div>

        <button
          class="btn-primary upgrade-btn"
          [disabled]="loading()"
          (click)="startCheckout()"
        >
          {{ loading() ? "Redirecting…" : "Upgrade now" }}
        </button>

        <p *ngIf="errorMessage()" class="upgrade-error">{{ errorMessage() }}</p>
      </div>
    </div>
  `,
  styles: [`
    .upgrade-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 60vh;
    }
    .upgrade-card {
      max-width: 480px;
      width: 100%;
      text-align: center;
    }
    .plan-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
      margin: 1.5rem 0;
    }
    .plan-card {
      border: 2px solid var(--surface-border, #dee2e6);
      border-radius: 8px;
      padding: 1rem;
      cursor: pointer;
      transition: border-color 0.2s;
    }
    .plan-card--selected {
      border-color: var(--primary-color, #6366f1);
    }
    .plan-name {
      font-weight: 600;
      margin: 0 0 0.25rem;
    }
    .plan-desc {
      font-size: 0.85rem;
      color: var(--text-color-secondary, #6c757d);
      margin: 0;
    }
    .upgrade-btn {
      width: 100%;
      margin-top: 0.5rem;
    }
    .upgrade-error {
      color: var(--red-500, #ef4444);
      margin-top: 0.75rem;
      font-size: 0.9rem;
    }
  `]
})
export class UpgradePageComponent {
  private readonly api = inject(TrackerApiService);

  readonly selectedPlan = signal<"monthly" | "annual">("monthly");
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  startCheckout(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.createCheckout(this.selectedPlan()).subscribe({
      next: ({ checkoutUrl }) => {
        window.location.href = checkoutUrl;
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set("Failed to start checkout. Please try again.");
      }
    });
  }
}
