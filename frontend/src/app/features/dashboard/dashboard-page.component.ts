import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { TableModule } from "primeng/table";
import { TagModule } from "primeng/tag";
import { TooltipModule } from "primeng/tooltip";
import { ChartModule } from "primeng/chart";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";
import { DashboardStats } from "../../shared/models";

@Component({
  standalone: true,
  selector: "app-dashboard-page",
  imports: [CommonModule, TableModule, TagModule, TooltipModule, ChartModule],
  template: `
    <h2 class="page-title">{{ i18n.t().dashboardTitle }}</h2>
    <p class="section-subtitle">{{ i18n.t().dashboardSubtitle }}</p>
    <div *ngIf="loading(); else content" class="card">{{ i18n.t().loadingDashboard }}</div>

    <ng-template #content>
      <div class="kpi-grid" *ngIf="stats() as s; else noStats">
        <article class="kpi-card">
          <p class="kpi-label">{{ i18n.t().kpiMonthlySpend }}</p>
          <p class="kpi-value">{{ currency(s.totalMonthlySpend) }}</p>
          <span class="kpi-hint">{{ i18n.t().kpiAnnualRunRate }} {{ currency(s.totalAnnualSpend) }}</span>
        </article>

        <article class="kpi-card">
          <p class="kpi-label">{{ i18n.t().kpiRenewalsIn30 }}</p>
          <p class="kpi-value">{{ s.renewingIn30Days.length }}</p>
          <span class="kpi-hint">{{ i18n.t().kpiDueAmount }} {{ currency(s.totalRenewal30DaysAmount) }}</span>
        </article>

        <article class="kpi-card">
          <p class="kpi-label">{{ i18n.t().kpiActiveSubscriptions }}</p>
          <p class="kpi-value">{{ s.subscriptionCount }}</p>
          <span class="kpi-hint">{{ i18n.t().kpiTrackedCategories }} {{ categoryChartData().length }}</span>
        </article>

        <article class="kpi-card">
          <p class="kpi-label">
            {{ i18n.t().kpiYoySpend }}
            <i
              class="pi pi-question-circle info-icon"
              [pTooltip]="i18n.t().kpiYoyTooltip"
              tooltipPosition="top"
            ></i>
          </p>
          <p class="kpi-value">{{ yoyGrowth() }}</p>
          <span class="kpi-hint">{{ yoyPeriod() }}</span>
        </article>

        <article class="kpi-card accent">
          <p class="kpi-label">
            {{ i18n.t().kpiPotentialSavings }}
            <i
              class="pi pi-question-circle info-icon"
              [pTooltip]="i18n.t().kpiPotentialSavingsTooltip"
              tooltipPosition="top"
            ></i>
          </p>
          <p class="kpi-value">{{ currency(s.potentialSavings) }}</p>
          <span class="kpi-hint">{{ i18n.t().kpiDuplicateWarnings }} {{ s.duplicateWarnings.length }}</span>
        </article>
      </div>

      <ng-template #noStats>
        <section class="card mt-3">{{ i18n.t().noDashboardData }}</section>
      </ng-template>

      <section class="card mt-3">
        <div class="section-head">
          <h3>{{ i18n.t().renewingIn30Title }}</h3>
          <p-tag
            [value]="vendorsTagValue(stats()?.renewingIn30Days?.length ?? 0)"
            [severity]="(stats()?.renewingIn30Days?.length ?? 0) > 0 ? 'warning' : 'success'"
          />
        </div>
        <p-table [value]="stats()?.renewingIn30Days ?? []" responsiveLayout="scroll">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ i18n.t().thVendor }}</th>
              <th>{{ i18n.t().thAmountUsd }}</th>
              <th>{{ i18n.t().thRenewalDate }}</th>
              <th>{{ i18n.t().thDaysLeft }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-item>
            <tr>
              <td>{{ item.vendorName }}</td>
              <td>{{ currency(item.amountUsd) }}</td>
              <td>{{ item.renewalDate | date: "yyyy-MM-dd" }}</td>
              <td>
                <p-tag
                  [value]="item.daysLeft + 'd'"
                  [severity]="item.daysLeft <= 7 ? 'danger' : item.daysLeft <= 30 ? 'warning' : 'info'"
                />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="4">{{ i18n.t().noRenewalsIn30 }}</td>
            </tr>
          </ng-template>
        </p-table>
      </section>

      <section class="dashboard-grid mt-3">
        <div class="card category-card">
          <div class="section-head">
            <h3>{{ i18n.t().spendByCategory }}</h3>
            <span class="chip">{{ categoriesTagValue(categoryChartData().length) }}</span>
          </div>
          <div class="donut-wrap">
            <p-chart type="doughnut" [data]="categoryPieData()" [options]="categoryPieOptions" style="max-width:100%" />
          </div>
          <ul class="category-legend" *ngIf="categoryChartData().length > 0">
            <li *ngFor="let item of categoryChartData(); let i = index" class="legend-item">
              <span class="legend-dot" [style.background]="pieColors[i % pieColors.length]"></span>
              <span class="legend-label">{{ item.key }}</span>
              <span class="legend-value">{{ currency(item.value) }}</span>
            </li>
          </ul>
        </div>

        <div class="card trend-card">
          <div class="section-head">
            <h3>{{ i18n.t().monthlySpendTrend }}</h3>
            <span class="chip">{{ i18n.t().last12Months }}</span>
          </div>
          <div class="trend-chart-center">
            <p-chart type="line" [data]="trendLineData()" [options]="trendLineOptions" style="width:100%;height:250px" />
          </div>

          <div class="trend-mini" *ngIf="trendMiniBars().length > 0 && trendSummary() as summary">
            <div class="mini-head">
              <span>{{ i18n.t().recentMonthlyBreakdown }}</span>
              <small>{{ i18n.t().last6Months }}</small>
            </div>
            <div class="mini-bars">
              <div class="mini-col" *ngFor="let bar of trendMiniBars()">
                <div class="mini-bar-wrap">
                  <div class="mini-bar" [style.height.%]="bar.heightPercent"></div>
                </div>
                <span class="mini-value">{{ compactCurrency(bar.value) }}</span>
                <span class="mini-label">{{ bar.label }}</span>
              </div>
            </div>

            <div class="trend-insights">
              <div class="insight-pill">
                <span class="insight-label">{{ i18n.t().insightPeakMonth }}</span>
                <strong>{{ summary.peakLabel }} - {{ compactCurrency(summary.peakValue) }}</strong>
              </div>
              <div class="insight-pill">
                <span class="insight-label">{{ i18n.t().insight12mAvg }}</span>
                <strong>{{ compactCurrency(summary.averageValue) }}</strong>
              </div>
              <div class="insight-pill">
                <span class="insight-label">{{ i18n.t().insightMomChange }}</span>
                <strong [class.up]="summary.momChangePercent >= 0" [class.down]="summary.momChangePercent < 0">
                  {{ summary.momChangePercent >= 0 ? "+" : "" }}{{ summary.momChangePercent.toFixed(1) }}%
                </strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="card mt-3" *ngIf="(stats()?.topCostDrivers?.length ?? 0) > 0">
        <div class="section-head">
          <h3>{{ i18n.t().topCostDrivers }}</h3>
          <span class="chip">{{ i18n.t().monthlyNormalized }}</span>
        </div>
        <div class="drivers-list">
          <div class="driver-row" *ngFor="let item of stats()?.topCostDrivers">
            <div>
              <strong>{{ item.vendorName }}</strong>
              <small>{{ subscriptionCountLabel(item.subscriptionCount) }}</small>
            </div>
            <div class="driver-amount">{{ currency(item.monthlySpendUsd) }}</div>
          </div>
        </div>
      </section>

      <section class="card mt-3" *ngIf="(stats()?.duplicateWarnings?.length ?? 0) > 0">
        <div class="section-head">
          <h3>{{ i18n.t().duplicateToolingWarnings }}</h3>
          <p-tag [value]="i18n.t().optimizationOpportunity" severity="info"></p-tag>
        </div>
        <ul class="warnings">
          <li *ngFor="let warning of stats()?.duplicateWarnings">
            <strong>{{ warning.type }}</strong>
            <span>{{ warning.key }} ({{ warning.subscriptionIds.length }} tools)</span>
            <span class="savings">{{ i18n.t().estimatedSavings }} {{ currency(warning.estimatedSavingsUsd) }}</span>
          </li>
        </ul>
      </section>
    </ng-template>
  `,
  styles: [
    `
      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(5, minmax(0, 1fr));
        gap: 12px;
        margin-top: 8px;
      }
      .kpi-card {
        padding: 14px;
        border-radius: 14px;
        border: 1px solid #d8e1ef;
        background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
      }
      .kpi-card.accent {
        background: linear-gradient(180deg, #eff6ff 0%, #f5faff 100%);
        border-color: #c9dcff;
      }
      .kpi-label {
        margin: 0;
        color: #51627d;
        font-size: 0.86rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }
      .kpi-value {
        margin: 6px 0;
        font-size: clamp(1.2rem, 2vw, 1.7rem);
        font-weight: 700;
      }
      .kpi-hint {
        color: #64748b;
        font-size: 0.86rem;
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
      .dashboard-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
        align-items: stretch;
      }
      .dashboard-grid > .card {
        height: 100%;
      }
      .donut-wrap {
        max-width: 260px;
        margin: 0 auto;
      }
      .category-legend {
        list-style: none;
        margin: 16px 0 0 0;
        padding: 0;
        display: grid;
        gap: 7px;
      }
      .legend-item {
        display: flex;
        align-items: center;
        gap: 9px;
        font-size: 0.88rem;
      }
      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        flex-shrink: 0;
      }
      .legend-label {
        flex: 1;
        color: #334155;
        text-transform: capitalize;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .legend-value {
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
      }
      .trend-card {
        display: grid;
        grid-template-rows: auto minmax(0, 1fr) auto;
      }
      .trend-chart-center {
        display: grid;
        align-items: center;
        min-height: 0;
      }
      .trend-chart-center p-chart {
        display: block;
        width: 100%;
        min-height: 250px;
      }
      .trend-card .trend-mini {
        margin-top: 8px;
      }
      .trend-mini {
        margin-top: 12px;
        border-top: 1px dashed #d6dfec;
        padding-top: 12px;
      }
      .mini-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        color: #334155;
        font-size: 0.9rem;
      }
      .mini-head small {
        color: #64748b;
      }
      .mini-bars {
        display: grid;
        grid-template-columns: repeat(6, minmax(0, 1fr));
        gap: 10px;
        align-items: end;
      }
      .mini-col {
        display: grid;
        justify-items: center;
        gap: 5px;
      }
      .mini-bar-wrap {
        width: 100%;
        height: 88px;
        border: 1px solid #dbe5f4;
        border-radius: 8px;
        background: linear-gradient(180deg, #f8fbff 0%, #edf3fb 100%);
        position: relative;
        display: flex;
        align-items: end;
        overflow: hidden;
      }
      .mini-bar {
        width: 100%;
        min-height: 4px;
        background: linear-gradient(180deg, #3b82f6 0%, #1d4ed8 100%);
        position: relative;
      }
      .mini-value {
        font-size: 0.72rem;
        color: #334155;
        font-weight: 600;
      }
      .mini-label {
        font-size: 0.76rem;
        color: #64748b;
      }
      .trend-insights {
        margin-top: 10px;
        display: grid;
        gap: 8px;
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
      .insight-pill {
        border: 1px solid #dbe5f4;
        border-radius: 10px;
        background: #f8fbff;
        padding: 8px 10px;
        display: grid;
        gap: 4px;
      }
      .insight-label {
        font-size: 0.74rem;
        color: #64748b;
        text-transform: uppercase;
        letter-spacing: 0.03em;
      }
      .insight-pill strong {
        font-size: 0.9rem;
        color: #0f172a;
      }
      .insight-pill strong.up {
        color: #166534;
      }
      .insight-pill strong.down {
        color: #b91c1c;
      }
      .warnings {
        margin: 0;
        padding-left: 18px;
        display: grid;
        gap: 8px;
      }
      .drivers-list {
        display: grid;
        gap: 6px;
      }
      .driver-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 10px;
        border: 1px solid #dbe5f4;
        border-radius: 10px;
        padding: 8px 10px;
        background: #f8fbff;
      }
      .driver-row small {
        display: block;
        color: #64748b;
        margin-top: 2px;
      }
      .driver-amount {
        font-weight: 700;
        color: #0f172a;
      }
      .savings {
        color: #0c4a6e;
        margin-left: 6px;
      }
      @media (max-width: 1050px) {
        .kpi-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .dashboard-grid {
          grid-template-columns: 1fr;
        }
        .trend-insights {
          grid-template-columns: 1fr;
        }
      }
      @media (max-width: 640px) {
        .kpi-grid {
          grid-template-columns: 1fr;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly loading = signal(true);
  readonly stats = signal<DashboardStats | null>(null);
  readonly yoyGrowth = signal("n/a");
  readonly yoyPeriod = signal("");
  readonly categoryChartData = computed(() =>
    Object.entries(this.stats()?.spendByCategory ?? {}).map(([key, value]) => ({
      key,
      value: Number(value)
    }))
  );
  readonly trendChartData = computed(() =>
    (this.stats()?.monthlyTrend ?? []).map((item) => ({
      key: item.month,
      value: Number(item.amountUsd)
    }))
  );
  readonly trendMiniBars = computed(() => {
    const source = this.trendChartData().slice(-6);
    if (source.length === 0) return [];
    const max = Math.max(...source.map((item) => item.value), 1);
    return source.map((item) => ({
      label: this.shortMonth(item.key),
      value: item.value,
      heightPercent: Math.max((item.value / max) * 100, 4)
    }));
  });
  readonly trendSummary = computed(() => {
    const source = this.trendChartData();
    if (source.length === 0) return null;

    const peak = source.reduce((currentPeak, item) => (item.value > currentPeak.value ? item : currentPeak), source[0]);
    const averageValue = source.reduce((sum, item) => sum + item.value, 0) / source.length;
    const current = source[source.length - 1];
    const previous = source[source.length - 2];
    const momChangePercent =
      previous && previous.value > 0 ? ((current.value - previous.value) / previous.value) * 100 : current.value > 0 ? 100 : 0;

    return {
      peakLabel: this.shortMonth(peak.key),
      peakValue: peak.value,
      averageValue,
      momChangePercent
    };
  });

  protected readonly pieColors = ["#1473e6", "#ff8f1f", "#16a34a", "#e11d48", "#7c3aed", "#0e7490"];

  readonly categoryPieData = computed(() => {
    const items = this.categoryChartData();
    return {
      labels: items.map((i) => i.key),
      datasets: [{
        data: items.map((i) => i.value),
        backgroundColor: items.map((_, idx) => this.pieColors[idx % this.pieColors.length])
      }]
    };
  });
  readonly categoryPieOptions = { responsive: true, plugins: { legend: { display: false } } };

  readonly trendLineData = computed(() => {
    const items = this.trendChartData();
    return {
      labels: items.map((i) => this.shortMonth(i.key)),
      datasets: [{
        label: this.i18n.t().monthlySpendDatasetLabel,
        data: items.map((i) => i.value),
        borderColor: "#1473e6",
        backgroundColor: "rgba(20, 115, 230, 0.15)",
        fill: true,
        tension: 0.3,
        pointRadius: 3,
        pointBackgroundColor: "#1473e6"
      }]
    };
  });
  readonly trendLineOptions = { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } };

  constructor() {
    this.api
      .getDashboardStats()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (value) => {
          this.stats.set(value);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      });

    this.api
      .getAnalytics()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const yoy = result.yoySpendComparison;
          if (yoy.dataAvailable) {
            this.yoyGrowth.set(`${yoy.growthPercent}%`);
            this.yoyPeriod.set(`${yoy.previousYear} → ${yoy.currentYear}`);
          }
        }
      });
  }

  subscriptionCountLabel(count: number): string {
    if (this.i18n.lang() === "ru") {
      return this.i18n.pluralRu(count, "подписка", "подписки", "подписок");
    }
    return `${count} subscription${count !== 1 ? "s" : ""}`;
  }

  vendorsTagValue(count: number): string {
    if (this.i18n.lang() === "ru") {
      return this.i18n.pluralRu(count, "поставщик", "поставщика", "поставщиков");
    }
    return `${count} vendors`;
  }

  categoriesTagValue(count: number): string {
    if (this.i18n.lang() === "ru") {
      return this.i18n.pluralRu(count, "категория", "категории", "категорий");
    }
    return `${count} categories`;
  }

  currency(value: string | number | null | undefined): string {
    const amount = Number(value ?? 0);
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      maximumFractionDigits: 2
    }).format(Number.isFinite(amount) ? amount : 0);
  }

  compactCurrency(value: string | number | null | undefined): string {
    const amount = Number(value ?? 0);
    if (!Number.isFinite(amount)) return "$0";
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      notation: "compact",
      maximumFractionDigits: 1
    }).format(amount);
  }

  private shortMonth(raw: string): string {
    const [year, month] = raw.split("-");
    const y = Number(year);
    const m = Number(month);
    if (!Number.isFinite(y) || !Number.isFinite(m)) return raw;
    const date = new Date(Date.UTC(y, Math.max(0, m - 1), 1));
    const locale = this.i18n.lang() === "ru" ? "ru-RU" : "en-US";
    return new Intl.DateTimeFormat(locale, {
      month: "short",
      year: "2-digit",
      timeZone: "UTC"
    }).format(date);
  }
}
