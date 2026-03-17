import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subject, debounceTime, distinctUntilChanged } from "rxjs";
import { ConfirmationService, MessageService } from "primeng/api";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { TableModule } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { DropdownModule } from "primeng/dropdown";
import { TagModule } from "primeng/tag";
import { CardModule } from "primeng/card";
import { PaginatorModule } from "primeng/paginator";
import { AutoCompleteModule, AutoCompleteCompleteEvent } from "primeng/autocomplete";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { SubscriptionStore } from "../../core/services/subscription.store";
import { I18nService } from "../../core/services/i18n.service";
import { SubscriptionItem } from "../../shared/models";

@Component({
  standalone: true,
  selector: "app-subscriptions-page",
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    DropdownModule,
    TagModule,
    CardModule,
    ConfirmDialogModule,
    PaginatorModule,
    AutoCompleteModule
  ],
  template: `
    <p-confirmDialog />
    <h2 class="page-title">{{ i18n.t().subscriptionsTitle }}</h2>
    <p class="section-subtitle">{{ i18n.t().subscriptionsSubtitle }}</p>

    <section class="card mb-3">
      <div class="section-head">
        <h3>{{ i18n.t().createSubscriptionTitle }}</h3>
        <button pButton type="button" [label]="i18n.t().importCsvBtn" icon="pi pi-upload" class="p-button-outlined" (click)="csvInput.click()"></button>
      </div>
      <input #csvInput type="file" accept=".csv" class="hidden-input" (change)="importCsv($event)" />

      <form [formGroup]="form" (ngSubmit)="createSubscription()" class="form-grid">
        <div class="field">
          <label>{{ i18n.t().fieldVendorName }}</label>
          <div class="field-control">
            <input
              pInputText
              formControlName="vendorName"
              [placeholder]="isInvalid('vendorName') ? i18n.t().vendorNameRequiredPlaceholder : 'Slack'"
              [class.input-inline-error]="isInvalid('vendorName')"
            />
          </div>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldCategory }}</label>
          <div class="field-control">
            <p-autoComplete
              formControlName="category"
              [suggestions]="filteredCategories"
              (completeMethod)="filterCategories($event)"
              [forceSelection]="false"
              [dropdown]="true"
              [placeholder]="isInvalid('category') ? i18n.t().categoryRequiredPlaceholder : i18n.t().selectOrTypeCategoryPlaceholder"
              [class.input-inline-error]="isInvalid('category')"
              styleClass="w-full"
            ></p-autoComplete>
          </div>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldAmount }}</label>
          <div class="field-control">
            <input
              pInputText
              formControlName="amount"
              [placeholder]="isInvalid('amount') ? i18n.t().amountRequiredPlaceholder : '120.00'"
              [class.input-inline-error]="isInvalid('amount')"
            />
          </div>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldCurrency }}</label>
          <p-dropdown [options]="currencies" formControlName="currency" optionLabel="label" optionValue="value"></p-dropdown>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldBillingCycle }}</label>
          <p-dropdown [options]="cycles()" formControlName="billingCycle" optionLabel="label" optionValue="value"></p-dropdown>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldPaymentMode }}</label>
          <p-dropdown [options]="paymentModes()" formControlName="paymentMode" optionLabel="label" optionValue="value"></p-dropdown>
        </div>

        <div class="field">
          <label [class.field-label-error]="isInvalid('renewalDate')">
            {{ i18n.t().fieldRenewalDate }}
            <span class="label-inline-error" *ngIf="isInvalid('renewalDate') && !form.controls.renewalDate.value">{{ i18n.t().fieldRequired }}</span>
          </label>
          <div class="field-control">
            <input pInputText type="date" formControlName="renewalDate" [class.input-inline-error]="isInvalid('renewalDate')" />
          </div>
        </div>

        <div class="field">
          <label>
            {{ i18n.t().fieldNextPaymentDate }}
            <span class="label-meta">{{ form.controls.paymentMode.value === "MANUAL" ? i18n.t().labelRequiredForManual : i18n.t().labelOptionalForAutomatic }}</span>
          </label>
          <div class="field-control">
            <input pInputText type="date" formControlName="nextPaymentDate" [class.input-inline-error]="isInvalid('nextPaymentDate')" />
          </div>
        </div>

        <div class="field span-2">
          <label>{{ i18n.t().fieldVendorUrlOptional }}</label>
          <input pInputText formControlName="vendorUrl" placeholder="https://vendor.com" />
        </div>

        <div class="field actions">
          <button pButton type="submit" [label]="i18n.t().btnSaveSubscription" [loading]="saving()"></button>
        </div>
      </form>

      <div class="warning-panel" *ngIf="duplicateWarning()">
        <i class="pi pi-exclamation-triangle"></i>
        <span>{{ duplicateWarning() }}</span>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <h3>{{ i18n.t().currentSubscriptionsTitle }}</h3>
        <div class="filter-bar">
          <input
            pInputText
            [value]="vendorSearch()"
            (input)="onVendorInput($event)"
            [placeholder]="i18n.t().searchVendorPlaceholder"
            class="search-input"
          />
          <p-dropdown
            [options]="categoryOptions"
            [ngModel]="categoryFilter()"
            (ngModelChange)="onCategoryChange($event)"
            [placeholder]="i18n.t().allCategoriesPlaceholder"
            [showClear]="true"
            optionLabel="label"
            optionValue="value"
            class="filter-dropdown"
          ></p-dropdown>
          <p-dropdown
            [options]="statusOptions()"
            [ngModel]="statusFilter()"
            (ngModelChange)="onStatusChange($event)"
            [placeholder]="i18n.t().allStatusesPlaceholder"
            [showClear]="true"
            optionLabel="label"
            optionValue="value"
            class="filter-dropdown"
          ></p-dropdown>
          <button
            pButton
            type="button"
            [label]="i18n.t().exportCsvBtn"
            icon="pi pi-download"
            class="p-button-outlined p-button-sm"
            [loading]="exportingFormat() === 'csv'"
            (click)="exportFile('csv')"
          ></button>
          <button
            pButton
            type="button"
            [label]="i18n.t().exportPdfBtn"
            icon="pi pi-file-pdf"
            class="p-button-outlined p-button-sm"
            [loading]="exportingFormat() === 'pdf'"
            (click)="exportFile('pdf')"
          ></button>
        </div>
      </div>

      <p-table [value]="store.items()" responsiveLayout="scroll">
        <ng-template pTemplate="header">
          <tr>
            <th>{{ i18n.t().thVendor }}</th>
            <th>{{ i18n.t().thCategory }}</th>
            <th>{{ i18n.t().thAmount }}</th>
            <th>{{ i18n.t().thRenewal }}</th>
            <th>{{ i18n.t().thPayment }}</th>
            <th class="actions-col">{{ i18n.t().thActions }}</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-sub>
          <tr>
            <td>
              <div class="vendor-cell">
                <span>{{ sub.vendorName }}</span>
                <small *ngIf="sub.vendorUrl">{{ sub.vendorUrl }}</small>
              </div>
            </td>
            <td>{{ sub.category }}</td>
            <td>{{ currency(sub.amount, sub.currency) }}</td>
            <td>{{ sub.renewalDate | date: "yyyy-MM-dd" }}</td>
            <td>
              <div class="payment-cell">
                <p-tag [value]="paymentLabel(sub)" [severity]="paymentSeverity(sub)" />
                <small *ngIf="paymentHint(sub) as hint">{{ hint }}</small>
              </div>
            </td>
            <td class="actions-col">
              <button
                *ngIf="sub.paymentMode === 'MANUAL'"
                pButton
                type="button"
                class="p-button-sm p-button-success p-button-text"
                icon="pi pi-check-circle"
                [loading]="markingPaidId() === sub.id"
                (click)="markAsPaid(sub)"
                [label]="i18n.t().btnMarkAsPaid"
              ></button>
              <button
                pButton
                type="button"
                class="p-button-sm p-button-danger p-button-text"
                icon="pi pi-trash"
                (click)="removeSubscription(sub)"
              ></button>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="6">
              {{ hasActiveFilters() ? i18n.t().noMatchesForFilters : i18n.t().noSubscriptionsYet }}
            </td>
          </tr>
        </ng-template>
      </p-table>

      <p-paginator
        *ngIf="store.total() > 0"
        [rows]="store.size()"
        [totalRecords]="store.total()"
        [rowsPerPageOptions]="[10, 25, 50, 100]"
        (onPageChange)="onPageChange($event)"
        styleClass="mt-2"
      ></p-paginator>
    </section>
  `,
  styles: [
    `
      .section-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        margin-bottom: 12px;
        flex-wrap: wrap;
      }
      .section-head h3 {
        margin: 0;
      }
      .filter-bar {
        display: flex;
        gap: 8px;
        align-items: center;
        flex-wrap: wrap;
      }
      .hidden-input {
        display: none;
      }
      .form-grid {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 12px;
      }
      .field {
        display: grid;
        gap: 6px;
      }
      .field label {
        color: #475569;
        font-size: 0.9rem;
      }
      .field label.field-label-error {
        color: #b91c1c;
      }
      .label-meta {
        color: #64748b;
        font-size: 0.78rem;
        margin-left: 6px;
        text-transform: lowercase;
      }
      .label-inline-error {
        margin-left: 6px;
        color: #dc2626;
        font-size: 0.78rem;
        text-transform: lowercase;
      }
      .field :is(input, .p-dropdown) {
        width: 100%;
      }
      .field-control {
        position: relative;
      }
      .input-inline-error {
        border-color: #ef4444 !important;
      }
      .input-inline-error::placeholder {
        color: #dc2626;
        opacity: 1;
      }
      .span-2 {
        grid-column: span 2;
      }
      .actions {
        display: flex;
        align-items: flex-end;
      }
      .warning-panel {
        margin-top: 12px;
        border: 1px solid #fed7aa;
        background: #fff7ed;
        color: #9a3412;
        border-radius: 10px;
        padding: 10px 12px;
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .search-input {
        min-width: 200px;
      }
      .filter-dropdown {
        min-width: 150px;
      }
      .vendor-cell {
        display: grid;
        gap: 2px;
      }
      .vendor-cell small {
        color: #64748b;
        max-width: 280px;
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
      }
      .actions-col {
        text-align: right;
      }
      .actions-col :is(button + button) {
        margin-left: 4px;
      }
      .payment-cell {
        display: grid;
        gap: 4px;
      }
      .payment-cell small {
        color: #64748b;
      }
      .mt-2 {
        margin-top: 8px;
      }
      @media (max-width: 1100px) {
        .form-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .span-2 {
          grid-column: span 2;
        }
      }
      @media (max-width: 700px) {
        .form-grid {
          grid-template-columns: 1fr;
        }
        .span-2 {
          grid-column: span 1;
        }
        .search-input,
        .filter-dropdown {
          min-width: 100%;
        }
        .section-head {
          align-items: stretch;
          flex-direction: column;
        }
        .filter-bar {
          flex-direction: column;
          align-items: stretch;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubscriptionsPageComponent {
  protected readonly i18n = inject(I18nService);
  readonly store = inject(SubscriptionStore);
  private readonly api = inject(TrackerApiService);
  private readonly messageService = inject(MessageService, { optional: true });
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly duplicateWarning = signal<string | null>(null);
  readonly saving = signal(false);
  readonly markingPaidId = signal<string | null>(null);
  readonly exportingFormat = signal<"csv" | "pdf" | null>(null);
  readonly vendorSearch = signal("");
  readonly categoryFilter = signal("");
  readonly statusFilter = signal("");

  allCategories: string[] = [];
  filteredCategories: string[] = [];

  private readonly vendorSearch$ = new Subject<string>();

  readonly cycles = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.cycleMonthly, value: "MONTHLY" },
      { label: t.cycleQuarterly, value: "QUARTERLY" },
      { label: t.cycleAnnual, value: "ANNUAL" }
    ];
  });

  readonly currencies = [
    { label: "USD", value: "USD" },
    { label: "EUR", value: "EUR" },
    { label: "GBP", value: "GBP" }
  ];

  readonly paymentModes = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.paymentAutomatic, value: "AUTO" },
      { label: t.paymentManual, value: "MANUAL" }
    ];
  });

  categoryOptions: { label: string; value: string }[] = [];

  readonly statusOptions = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.statusActive, value: "ACTIVE" },
      { label: t.statusCanceled, value: "CANCELED" },
      { label: t.statusPaused, value: "PAUSED" },
      { label: t.statusExpired, value: "EXPIRED" }
    ];
  });

  readonly form = this.fb.group({
    vendorName: ["", [Validators.required]],
    category: ["", [Validators.required]],
    amount: ["", [Validators.required]],
    currency: ["USD", [Validators.required]],
    billingCycle: ["MONTHLY", [Validators.required]],
    paymentMode: ["AUTO", [Validators.required]],
    renewalDate: ["", [Validators.required]],
    nextPaymentDate: [""],
    vendorUrl: [""]
  });

  hasActiveFilters(): boolean {
    return !!(this.vendorSearch() || this.categoryFilter() || this.statusFilter());
  }

  constructor() {
    this.store.load();

    this.api.getCategories().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ predefined, custom }) => {
      this.allCategories = [...predefined, ...custom];
      this.categoryOptions = this.allCategories.map((c) => ({
        label: c.charAt(0).toUpperCase() + c.slice(1),
        value: c
      }));
    });

    this.vendorSearch$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((vendor) => {
        this.store.load({ vendor, page: 1 });
      });

    this.form.controls.paymentMode.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((mode) => {
      const nextPayment = this.form.controls.nextPaymentDate;
      if (mode === "MANUAL") {
        nextPayment.setValidators([Validators.required]);
        if (!nextPayment.value) {
          nextPayment.setValue(this.form.controls.renewalDate.value);
        }
      } else {
        nextPayment.clearValidators();
      }
      nextPayment.updateValueAndValidity({ emitEvent: false });
    });

    this.form.controls.category.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((category) => {
      const candidate = (category ?? "").trim().toLowerCase();
      if (!candidate) {
        this.duplicateWarning.set(null);
        return;
      }
      const duplicate = this.store.items().find((item) => item.category.toLowerCase() === candidate);
      if (duplicate) {
        const t = this.i18n.t();
        this.duplicateWarning.set(`${t.duplicateWarningPrefix} ${duplicate.vendorName} ${t.duplicateWarningSuffix}`);
      } else {
        this.duplicateWarning.set(null);
      }
    });
  }

  onVendorInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.vendorSearch.set(value);
    this.vendorSearch$.next(value);
  }

  onCategoryChange(value: string | null): void {
    this.categoryFilter.set(value ?? "");
    this.store.load({ category: value ?? "", page: 1 });
  }

  onStatusChange(value: string | null): void {
    this.statusFilter.set(value ?? "");
    this.store.load({ status: value ?? "", page: 1 });
  }

  onPageChange(event: { first?: number; rows?: number; page?: number }): void {
    this.store.load({ page: (event.page ?? 0) + 1, size: event.rows ?? this.store.size() });
  }

  filterCategories(event: AutoCompleteCompleteEvent): void {
    const query = event.query.toLowerCase();
    this.filteredCategories = this.allCategories.filter((c) => c.includes(query));
  }

  createSubscription(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.form.getRawValue();
    const t = this.i18n.t();
    this.saving.set(true);
    this.api
      .createSubscription({
        vendorName: payload.vendorName?.trim(),
        vendorUrl: payload.vendorUrl?.trim() || null,
        category: payload.category?.trim(),
        amount: payload.amount?.trim(),
        currency: payload.currency,
        billingCycle: payload.billingCycle,
        renewalDate: payload.renewalDate,
        autoRenews: payload.paymentMode !== "MANUAL",
        paymentMode: payload.paymentMode,
        nextPaymentDate: this.normalizeOptionalDate(payload.nextPaymentDate),
        status: "ACTIVE",
        tags: []
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.form.reset({ currency: "USD", billingCycle: "MONTHLY", paymentMode: "AUTO" });
          this.duplicateWarning.set(null);
          this.store.load({ page: 1 });
          this.messageService?.add({
            severity: "success",
            summary: t.msgSubscriptionSaved,
            detail: t.msgSubscriptionSavedDetail
          });
          this.saving.set(false);
        },
        error: () => {
          this.messageService?.add({
            severity: "error",
            summary: t.msgSaveFailed,
            detail: t.msgSaveFailedDetail
          });
          this.saving.set(false);
        }
      });
  }

  removeSubscription(subscription: SubscriptionItem): void {
    const t = this.i18n.t();
    this.confirmationService.confirm({
      message: `"${subscription.vendorName}"? ${t.msgArchiveConfirmSuffix}`,
      header: t.msgArchiveTitle,
      icon: "pi pi-archive",
      acceptLabel: t.msgArchiveLabel,
      rejectLabel: t.msgCancelLabel,
      acceptButtonStyleClass: "p-button-danger",
      accept: () => {
        this.store.remove(subscription.id);
        this.messageService?.add({
          severity: "info",
          summary: t.msgSubscriptionArchived,
          detail: `${subscription.vendorName} ${t.msgSubscriptionArchivedSuffix}`
        });
      }
    });
  }

  markAsPaid(subscription: SubscriptionItem): void {
    const t = this.i18n.t();
    this.markingPaidId.set(subscription.id);
    this.api
      .markSubscriptionPaid(subscription.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.store.load();
          this.messageService?.add({
            severity: "success",
            summary: t.msgPaymentRecorded,
            detail: `${subscription.vendorName} ${t.msgPaymentRecordedSuffix}`
          });
          this.markingPaidId.set(null);
        },
        error: () => {
          this.messageService?.add({
            severity: "error",
            summary: t.msgPaymentUpdateFailed,
            detail: t.msgPaymentUpdateFailedDetail
          });
          this.markingPaidId.set(null);
        }
      });
  }

  importCsv(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    if (!file) return;

    this.api
      .importCsv(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const t = this.i18n.t();
          this.store.load({ page: 1 });
          const summary = `${t.msgImportedPrefix} ${result.imported}, ${t.msgSkippedPrefix} ${result.skipped}.`;
          this.messageService?.add({
            severity: result.errors.length === 0 ? "success" : "warn",
            summary: t.msgCsvImportCompleted,
            detail: result.errors.length > 0 ? `${summary} ${result.errors[0]}` : summary
          });
        },
        error: () => {
          const t = this.i18n.t();
          this.messageService?.add({
            severity: "error",
            summary: t.msgCsvImportFailed,
            detail: t.msgCsvImportFailedDetail
          });
        }
      });

    input.value = "";
  }

  exportFile(format: "csv" | "pdf"): void {
    this.exportingFormat.set(format);
    this.api
      .downloadExport(format, {
        category: this.categoryFilter() || undefined,
        status: this.statusFilter() || undefined
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `subscriptions.${format}`;
          a.click();
          URL.revokeObjectURL(url);
          this.exportingFormat.set(null);
        },
        error: () => {
          const t = this.i18n.t();
          this.messageService?.add({
            severity: "error",
            summary: t.msgExportFailed,
            detail: t.msgExportFailedDetail.replace("{0}", format.toUpperCase())
          });
          this.exportingFormat.set(null);
        }
      });
  }

  isInvalid(controlName: "vendorName" | "category" | "amount" | "renewalDate" | "nextPaymentDate"): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  paymentLabel(subscription: SubscriptionItem): string {
    const t = this.i18n.t();
    if (subscription.paymentMode === "AUTO") return t.payLabelAuto;

    const daysToPayment = this.daysUntil(subscription.nextPaymentDate);
    if (subscription.paymentStatus === "OVERDUE" || (daysToPayment !== null && daysToPayment < 0)) {
      return t.payLabelOverdue;
    }
    if (daysToPayment !== null && daysToPayment <= 7) {
      return t.payLabelDueSoon;
    }
    if (subscription.lastPaidAt || subscription.paymentStatus === "PAID") {
      return t.payLabelPaid;
    }
    return t.payLabelDueSoon;
  }

  paymentSeverity(subscription: SubscriptionItem): "info" | "success" | "warning" | "danger" {
    const rawMode = subscription.paymentMode === "AUTO" ? "AUTO" : null;
    if (rawMode === "AUTO") return "info";
    const daysToPayment = this.daysUntil(subscription.nextPaymentDate);
    if (subscription.paymentStatus === "OVERDUE" || (daysToPayment !== null && daysToPayment < 0)) return "danger";
    if (daysToPayment !== null && daysToPayment <= 7) return "warning";
    if (subscription.lastPaidAt || subscription.paymentStatus === "PAID") return "success";
    return "warning";
  }

  paymentHint(subscription: SubscriptionItem): string {
    const t = this.i18n.t();
    if (subscription.paymentMode === "AUTO") {
      if (subscription.nextPaymentDate) {
        return `${t.payHintNextCharge} ${subscription.nextPaymentDate}`;
      }
      return t.payHintAutoCharge;
    }

    if (subscription.paymentStatus === "OVERDUE" && subscription.nextPaymentDate) {
      return `${t.payHintWasDue} ${subscription.nextPaymentDate}`;
    }
    if (subscription.nextPaymentDate) {
      return `${t.payHintNextPayment} ${subscription.nextPaymentDate}`;
    }
    if (subscription.lastPaidAt) {
      return `${t.payHintLastPaid} ${subscription.lastPaidAt}`;
    }
    return t.payHintManual;
  }

  private daysUntil(dateString?: string | null): number | null {
    if (!dateString) return null;
    const [year, month, day] = dateString.split("-").map(Number);
    if (!year || !month || !day) return null;
    const targetUtc = Date.UTC(year, month - 1, day);
    const now = new Date();
    const todayUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
    return Math.floor((targetUtc - todayUtc) / (24 * 60 * 60 * 1000));
  }

  private normalizeOptionalDate(value?: string | null): string | null {
    const normalized = value?.trim();
    return normalized ? normalized : null;
  }

  currency(amount: string, currencyCode: string): string {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currencyCode || "USD",
      maximumFractionDigits: 2
    }).format(Number(amount));
  }
}
