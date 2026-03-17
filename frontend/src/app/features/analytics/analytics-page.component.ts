import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CardModule } from "primeng/card";
import { TableModule } from "primeng/table";
import { TagModule } from "primeng/tag";
import { TooltipModule } from "primeng/tooltip";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";
import { AnalyticsData } from "../../shared/models";

@Component({
  standalone: true,
  selector: "app-analytics-page",
  imports: [CommonModule, CardModule, TableModule, TagModule, TooltipModule],
  template: `
    <h2 class="page-title">{{ i18n.t().analyticsTitle }}</h2>
    <p class="section-subtitle">{{ i18n.t().analyticsSubtitle }}</p>

    <div *ngIf="loading(); else analyticsView" class="card">{{ i18n.t().loadingAnalytics }}</div>

    <ng-template #analyticsView>
      <ng-container *ngIf="analytics() as data; else noData">
        <section class="cards-grid">
          <article class="metric-card">
            <p class="metric-label">
              {{ i18n.t().kpiYoySpend }}
              <i
                class="pi pi-question-circle info-icon"
                [pTooltip]="i18n.t().yoyTooltip"
                tooltipPosition="top"
              ></i>
            </p>
            <ng-container *ngIf="data.yoySpendComparison.dataAvailable; else yoyNoData">
              <p class="metric-value">{{ data.yoySpendComparison.growthPercent }}%</p>
              <p class="metric-hint">
                {{ data.yoySpendComparison.currentYear }}: {{ currency(data.yoySpendComparison.currentYearUsd!) }}
              </p>
              <p class="metric-hint">
                {{ data.yoySpendComparison.previousYear }}: {{ currency(data.yoySpendComparison.previousYearUsd!) }}
              </p>
            </ng-container>
            <ng-template #yoyNoData>
              <p class="metric-value">—</p>
              <p class="metric-hint">{{ i18n.t().yoyNoData }}</p>
            </ng-template>
          </article>

          <article class="metric-card" *ngIf="data.budgetGauge as budget">
            <p class="metric-label">{{ i18n.t().budgetUtilization }}</p>
            <p class="metric-value">{{ budget.utilizationPercent }}%</p>
            <p class="metric-hint">{{ i18n.t().budgetLabel }} {{ currency(budget.budgetUsd) }}</p>
            <p class="metric-hint">{{ i18n.t().actualLabel }} {{ currency(budget.actualUsd) }}</p>
            <p-tag
              [value]="budget.overBudget ? i18n.t().overBudget : i18n.t().withinBudget"
              [severity]="budget.overBudget ? 'danger' : 'success'"
            />
          </article>

          <article class="metric-card">
            <p class="metric-label">{{ i18n.t().costPerEmployee }}</p>
            <p class="metric-value">{{ currency(data.costPerEmployee.monthlyUsd) }}</p>
            <p class="metric-hint">{{ i18n.t().teamSizeLabel }} {{ data.costPerEmployee.employeeCount }}</p>
            <p class="metric-hint">{{ i18n.t().annualLabel }} {{ currency(data.costPerEmployee.annualUsd) }}</p>
            <p-tag [value]="data.healthScore" [severity]="healthSeverity(data.healthScore)" />
          </article>
        </section>

        <section class="card mt-3">
          <div class="section-head">
            <h3>{{ i18n.t().fastestGrowingTitle }}</h3>
            <span class="chip">{{ trackedTagValue(data.fastestGrowingSubscriptions.length) }}</span>
          </div>
          <p-table [value]="data.fastestGrowingSubscriptions" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ i18n.t().thVendor }}</th>
                <th>{{ i18n.t().thPreviousMonthly }}</th>
                <th>{{ i18n.t().thCurrentMonthly }}</th>
                <th>{{ i18n.t().thGrowth }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-item>
              <tr>
                <td>{{ item.vendorName }}</td>
                <td>{{ currency(item.previousMonthlyUsd) }}</td>
                <td>{{ currency(item.currentMonthlyUsd) }}</td>
                <td>
                  <p-tag [value]="item.growthPercent + '%'" [severity]="growthSeverity(item.growthPercent)" />
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="4">{{ i18n.t().noHistoricalData }}</td>
              </tr>
            </ng-template>
          </p-table>
        </section>

      </ng-container>

      <ng-template #noData>
        <section class="card">{{ i18n.t().noAnalyticsData }}</section>
      </ng-template>
    </ng-template>
  `,
  styles: [
    `
      .cards-grid {
        margin-top: 8px;
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 12px;
      }
      .metric-card {
        background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
        border: 1px solid #d8e1ef;
        border-radius: 14px;
        padding: 14px;
      }
      .metric-label {
        margin: 0;
        color: #52627a;
        text-transform: uppercase;
        font-size: 0.82rem;
        letter-spacing: 0.05em;
      }
      .metric-value {
        margin: 8px 0;
        font-size: clamp(1.2rem, 2vw, 1.65rem);
        font-weight: 700;
      }
      .metric-hint {
        margin: 4px 0;
        color: #52627a;
      }
      .info-icon {
        margin-left: 6px;
        color: #64748b;
        font-size: 0.78rem;
        cursor: help;
      }
      .section-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        margin-bottom: 12px;
      }
      .section-head h3 {
        margin: 0;
      }
      .chip {
        border: 1px solid #c8d9ef;
        background: #f1f6fd;
        border-radius: 999px;
        padding: 5px 10px;
        color: #334155;
        font-size: 0.83rem;
      }
      @media (max-width: 1100px) {
        .cards-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
      }
      @media (max-width: 700px) {
        .cards-grid {
          grid-template-columns: 1fr;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AnalyticsPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly loading = signal(true);
  readonly analytics = signal<AnalyticsData | null>(null);

  constructor() {
    this.api
      .getAnalytics()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.analytics.set(result);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
  }

  trackedTagValue(count: number): string {
    if (this.i18n.lang() === "ru") {
      return this.i18n.pluralRu(count, "подписка", "подписки", "подписок");
    }
    return `${count} tracked`;
  }

  currency(value: string | number): string {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      maximumFractionDigits: 2
    }).format(Number(value));
  }

  healthSeverity(score: AnalyticsData["healthScore"]): "success" | "warning" | "danger" {
    if (score === "GOOD") return "success";
    if (score === "WARNING") return "warning";
    return "danger";
  }

  growthSeverity(percent: string): "success" | "warning" {
    return Number(percent) >= 0 ? "warning" : "success";
  }
}
