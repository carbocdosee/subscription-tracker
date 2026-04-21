import { ChangeDetectionStrategy, Component, Input, OnChanges, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router } from "@angular/router";
import { I18nService } from "../../core/services/i18n.service";
import { WeeklyInsights } from "../models";

const VISIBLE_LIMIT = 3;

interface InsightPill {
  label: string;
  detail: string;
  severity: "danger" | "warning" | "info" | "success";
  route: string;
}

@Component({
  standalone: true,
  selector: "app-insights-bar",
  imports: [CommonModule],
  template: `
    <div class="insights-bar">
      <!-- All-good state -->
      <div *ngIf="allGood(); else hasPills" class="all-good">
        <i class="pi pi-check-circle all-good-icon"></i>
        <span>{{ i18n.t().insightsAllGood }}</span>
      </div>

      <!-- Pills -->
      <ng-template #hasPills>
        <div class="pills-row">
          <div
            *ngFor="let pill of visiblePills()"
            class="insight-pill"
            [class.pill-danger]="pill.severity === 'danger'"
            [class.pill-warning]="pill.severity === 'warning'"
            [class.pill-info]="pill.severity === 'info'"
            (click)="navigate(pill.route)"
          >
            <span class="pill-label">{{ pill.label }}</span>
            <span class="pill-detail">{{ pill.detail }}</span>
          </div>

          <button *ngIf="hasMore() && !expanded()" class="see-all-btn" (click)="expanded.set(true)">
            {{ i18n.t().insightsSeeAll.replace("{0}", String(allPills().length)) }}
          </button>
          <button *ngIf="expanded() && allPills().length > VISIBLE_LIMIT" class="see-all-btn" (click)="expanded.set(false)">
            {{ i18n.t().insightsCollapse }}
          </button>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .insights-bar {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 10px 16px;
      margin-bottom: 12px;
    }
    .all-good {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #16a34a;
      font-size: 0.9rem;
      font-weight: 500;
    }
    .all-good-icon {
      font-size: 1rem;
    }
    .pills-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }
    .insight-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 0.82rem;
      font-weight: 500;
      cursor: pointer;
      transition: opacity 0.15s;
      user-select: none;
    }
    .insight-pill:hover {
      opacity: 0.85;
    }
    .pill-danger {
      background: #fee2e2;
      color: #b91c1c;
    }
    .pill-warning {
      background: #fef9c3;
      color: #854d0e;
    }
    .pill-info {
      background: #dbeafe;
      color: #1d4ed8;
    }
    .pill-label {
      font-weight: 600;
    }
    .pill-detail {
      opacity: 0.8;
    }
    .see-all-btn {
      background: none;
      border: none;
      color: #6366f1;
      font-size: 0.82rem;
      font-weight: 500;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: 4px;
    }
    .see-all-btn:hover {
      background: #ede9fe;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InsightsBarComponent implements OnChanges {
  @Input() insights: WeeklyInsights | null = null;

  protected readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly VISIBLE_LIMIT = VISIBLE_LIMIT;
  protected readonly expanded = signal(false);
  protected readonly allPills = signal<InsightPill[]>([]);

  protected readonly allGood = () => this.allPills().length === 0;
  protected readonly hasMore = () => this.allPills().length > VISIBLE_LIMIT;
  protected readonly visiblePills = () =>
    this.expanded() ? this.allPills() : this.allPills().slice(0, VISIBLE_LIMIT);

  ngOnChanges(): void {
    this.expanded.set(false);
    if (!this.insights) {
      this.allPills.set([]);
      return;
    }
    const t = this.i18n.t();
    const pills: InsightPill[] = [];

    for (const z of this.insights.zombieAlerts) {
      pills.push({
        label: z.vendor,
        detail: t.insightsDaysSince.replace("{0}", String(z.daysSinceUsed)),
        severity: "warning",
        route: "/subscriptions?zombie=true"
      });
    }

    for (const r of this.insights.renewalsThisWeek) {
      pills.push({
        label: r.vendor,
        detail: t.insightsDaysLeft.replace("{0}", String(r.daysLeft)),
        severity: r.daysLeft <= 2 ? "danger" : "info",
        route: "/subscriptions"
      });
    }

    for (const p of this.insights.priceIncreases) {
      pills.push({
        label: p.vendor,
        detail: t.insightsPriceLabel,
        severity: "danger",
        route: `/subscriptions?id=${p.id}`
      });
    }

    this.allPills.set(pills);
  }

  protected navigate(route: string): void {
    const [path, query] = route.split("?");
    const queryParams: Record<string, string> = {};
    if (query) {
      for (const part of query.split("&")) {
        const [k, v] = part.split("=");
        if (k) queryParams[k] = v ?? "";
      }
    }
    this.router.navigate([path], { queryParams });
  }

  protected String = String;
}
