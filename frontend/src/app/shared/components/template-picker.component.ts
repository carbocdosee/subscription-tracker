import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  Output,
  computed,
  inject,
  signal
} from "@angular/core";
import { NgIf, NgFor, NgClass } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { DropdownModule } from "primeng/dropdown";
import { ProgressSpinnerModule } from "primeng/progressspinner";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";
import { BatchCreateResponse, SaasTemplate } from "../models";

function addMonths(n: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

const RENEWAL_DATE_MONTHLY = addMonths(1);

@Component({
  selector: "app-template-picker",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgIf, NgFor, NgClass, FormsModule, ButtonModule, InputTextModule, DropdownModule, ProgressSpinnerModule],
  template: `
    <div class="tp-root">
      <!-- Toolbar -->
      <div class="tp-toolbar">
        <input
          pInputText
          type="text"
          [(ngModel)]="searchQuery"
          [placeholder]="i18n.t().templatePickerSearchPlaceholder"
          class="tp-search"
        />
        <p-dropdown
          [options]="categoryOptions()"
          [(ngModel)]="selectedCategory"
          optionLabel="label"
          optionValue="value"
          styleClass="tp-cat-filter"
        />
      </div>

      <!-- Loading -->
      <div *ngIf="loading()" class="tp-loading">
        <p-progressSpinner [style]="{ width: '36px', height: '36px' }" />
        <span>{{ i18n.t().templatePickerLoading }}</span>
      </div>

      <!-- Grid -->
      <div *ngIf="!loading()" class="tp-grid">
        <div
          *ngFor="let tpl of filtered()"
          class="tp-card"
          [ngClass]="{ 'tp-card--selected': isSelected(tpl.id) }"
          (click)="toggleTemplate(tpl)"
          role="checkbox"
          [attr.aria-checked]="isSelected(tpl.id)"
        >
          <div class="tp-card__check" *ngIf="isSelected(tpl.id)">
            <i class="pi pi-check"></i>
          </div>
          <img
            [src]="tpl.logoUrl"
            [alt]="tpl.name"
            class="tp-card__logo"
            (error)="onLogoError($event)"
          />
          <div class="tp-card__name">{{ tpl.name }}</div>
          <div class="tp-card__meta">
            <span class="tp-card__amount">\${{ tpl.defaultAmountUsd }}</span>
            <span class="tp-card__cycle">{{ i18n.t().templatePickerPerMonth }}</span>
          </div>
          <div class="tp-card__cat">{{ tpl.category }}</div>
        </div>

        <div *ngIf="filtered().length === 0" class="tp-empty">
          {{ i18n.t().templatePickerNoResults }}
        </div>
      </div>

      <!-- Footer -->
      <div class="tp-footer">
        <p class="tp-error" *ngIf="error()">{{ error() }}</p>
        <div class="tp-footer__actions">
          <button
            type="button"
            class="tp-custom-link"
            (click)="addCustom.emit()"
          >{{ i18n.t().templatePickerAddCustom }}</button>
          <p-button
            [label]="addLabel()"
            [disabled]="selectedIds().size === 0 || saving()"
            [loading]="saving()"
            (onClick)="submit()"
          />
        </div>
      </div>
    </div>
  `,
  styles: [`
    .tp-root {
      display: flex;
      flex-direction: column;
      gap: 14px;
    }
    .tp-toolbar {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }
    .tp-search {
      flex: 1;
      min-width: 160px;
    }
    .tp-loading {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 24px 0;
      color: #64748b;
    }
    .tp-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
      gap: 10px;
      max-height: 360px;
      overflow-y: auto;
    }
    .tp-card {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      padding: 10px 8px;
      border: 2px solid #e2e8f0;
      border-radius: 10px;
      cursor: pointer;
      transition: border-color 0.15s, background 0.15s;
      background: #fff;
      user-select: none;
    }
    .tp-card:hover {
      border-color: #94a3b8;
      background: #f8fafc;
    }
    .tp-card--selected {
      border-color: #6366f1;
      background: #eef2ff;
    }
    .tp-card__check {
      position: absolute;
      top: 5px;
      right: 5px;
      color: #6366f1;
      font-size: 0.75rem;
    }
    .tp-card__logo {
      width: 36px;
      height: 36px;
      object-fit: contain;
      border-radius: 6px;
    }
    .tp-card__name {
      font-size: 0.8rem;
      font-weight: 600;
      text-align: center;
      color: #1e293b;
      line-height: 1.2;
    }
    .tp-card__meta {
      display: flex;
      gap: 2px;
      align-items: baseline;
    }
    .tp-card__amount {
      font-size: 0.78rem;
      color: #475569;
    }
    .tp-card__cycle {
      font-size: 0.7rem;
      color: #94a3b8;
    }
    .tp-card__cat {
      font-size: 0.7rem;
      color: #6366f1;
      background: #eef2ff;
      padding: 1px 6px;
      border-radius: 10px;
    }
    .tp-card--selected .tp-card__cat {
      background: #c7d2fe;
    }
    .tp-empty {
      grid-column: 1 / -1;
      text-align: center;
      color: #94a3b8;
      padding: 20px 0;
    }
    .tp-footer {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .tp-footer__actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
    }
    .tp-custom-link {
      background: none;
      border: none;
      padding: 0;
      color: #6366f1;
      font-size: 0.88rem;
      cursor: pointer;
      text-decoration: underline;
    }
    .tp-custom-link:hover {
      color: #4f46e5;
    }
    .tp-error {
      color: #dc2626;
      font-size: 0.85rem;
      margin: 0;
    }
  `]
})
export class TemplatePickerComponent {
  /** Emitted after successful batch create. */
  @Output() readonly added = new EventEmitter<BatchCreateResponse>();
  /** Emitted when user clicks "Add custom tool". */
  @Output() readonly addCustom = new EventEmitter<void>();

  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  private readonly templates = signal<SaasTemplate[]>([]);
  readonly selectedIds = signal<Set<string>>(new Set());

  searchQuery = "";
  selectedCategory = "";

  readonly categoryOptions = computed(() => {
    const cats = [...new Set(this.templates().map(t => t.category))].sort();
    return [
      { label: this.i18n.t().templatePickerAllCategories, value: "" },
      ...cats.map(c => ({ label: c, value: c }))
    ];
  });

  readonly filtered = computed(() => {
    const q = this.searchQuery.toLowerCase().trim();
    const cat = this.selectedCategory;
    return this.templates().filter(t => {
      const matchQ = !q || t.name.toLowerCase().includes(q) || t.category.toLowerCase().includes(q);
      const matchCat = !cat || t.category === cat;
      return matchQ && matchCat;
    });
  });

  readonly addLabel = computed(() => {
    const n = this.selectedIds().size;
    return this.i18n.t().templatePickerAddNTools.replace("{0}", String(n));
  });

  constructor() {
    this.api.getTemplates()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          this.templates.set(res.templates);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
  }

  isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  toggleTemplate(tpl: SaasTemplate): void {
    const ids = new Set(this.selectedIds());
    if (ids.has(tpl.id)) {
      ids.delete(tpl.id);
    } else {
      ids.add(tpl.id);
    }
    this.selectedIds.set(ids);
  }

  onLogoError(event: Event): void {
    (event.target as HTMLImageElement).style.display = "none";
  }

  submit(): void {
    const selected = this.templates().filter(t => this.selectedIds().has(t.id));
    if (selected.length === 0) return;

    const items = selected.map(t => ({
      templateId: t.id,
      vendorName: t.name,
      category: t.category,
      amount: String(t.defaultAmountUsd),
      currency: "USD",
      billingCycle: t.defaultBillingCycle,
      renewalDate: RENEWAL_DATE_MONTHLY,
      vendorUrl: t.websiteUrl,
      logoUrl: t.logoUrl
    }));

    this.saving.set(true);
    this.error.set(null);

    this.api.batchCreateSubscriptions(items)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          this.saving.set(false);
          this.selectedIds.set(new Set());
          this.added.emit(res);
        },
        error: (err: { error?: { message?: string } }) => {
          this.saving.set(false);
          this.error.set(err?.error?.message ?? "Failed to add subscriptions.");
        }
      });
  }
}
