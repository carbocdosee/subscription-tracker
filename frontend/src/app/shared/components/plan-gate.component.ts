import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input
} from "@angular/core";
import { Router } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { I18nService } from "../../core/services/i18n.service";
import { PlanTier } from "../models";

@Component({
  selector: "app-plan-gate",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule],
  template: `
    <div class="plan-gate">
      <div class="plan-gate__icon">🔒</div>
      <h3 class="plan-gate__title">{{ i18n.t().planGateTitle }}</h3>
      <p class="plan-gate__subtitle">{{ subtitle() }}</p>
      <p-button
        [label]="i18n.t().planGateUpgradeBtn"
        icon="pi pi-arrow-up"
        (onClick)="goToPlans()"
      />
    </div>
  `,
  styles: [`
    .plan-gate {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      padding: 3rem 2rem;
      text-align: center;
      background: var(--surface-card);
      border: 1px dashed var(--surface-border);
      border-radius: 12px;
    }
    .plan-gate__icon { font-size: 2.5rem; }
    .plan-gate__title { margin: 0; font-size: 1.25rem; font-weight: 600; }
    .plan-gate__subtitle { margin: 0; color: var(--text-color-secondary); }
  `]
})
export class PlanGateComponent {
  readonly requiredPlan = input<PlanTier>("PRO");

  protected readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected subtitle(): string {
    const t = this.i18n.t();
    const planName = this.requiredPlan() === "PRO" ? t.planPro : t.planEnterprise;
    return t.planGateSubtitle.replace("{0}", planName);
  }

  protected goToPlans(): void {
    void this.router.navigate(["/billing/plans"]);
  }
}
