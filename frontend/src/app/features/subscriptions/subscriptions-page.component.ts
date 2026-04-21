import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subject, debounceTime, distinctUntilChanged } from "rxjs";
import { ConfirmationService, MessageService } from "primeng/api";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { DialogModule } from "primeng/dialog";
import { TableModule } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { InputTextareaModule } from "primeng/inputtextarea";
import { DropdownModule } from "primeng/dropdown";
import { TagModule } from "primeng/tag";
import { CardModule } from "primeng/card";
import { PaginatorModule } from "primeng/paginator";
import { CalendarModule } from "primeng/calendar";
import { ChipsModule } from "primeng/chips";
import { AutoCompleteModule, AutoCompleteCompleteEvent, AutoCompleteSelectEvent } from "primeng/autocomplete";
import { RouterLink } from "@angular/router";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { SubscriptionStore } from "../../core/services/subscription.store";
import { AuthSessionService } from "../../core/services/auth-session.service";
import { I18nService } from "../../core/services/i18n.service";
import { BatchCreateResponse, SubscriptionItem, VendorSuggestion } from "../../shared/models";
import { PlanGateComponent } from "../../shared/components/plan-gate.component";
import { TemplatePickerComponent } from "../../shared/components/template-picker.component";

@Component({
  standalone: true,
  selector: "app-subscriptions-page",
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputTextareaModule,
    DropdownModule,
    TagModule,
    CardModule,
    ConfirmDialogModule,
    DialogModule,
    PaginatorModule,
    CalendarModule,
    ChipsModule,
    AutoCompleteModule,
    RouterLink,
    PlanGateComponent,
    TemplatePickerComponent
  ],
  template: `
    <p-confirmDialog />
    <h2 class="page-title">{{ i18n.t().subscriptionsTitle }}</h2>
    <p class="section-subtitle">{{ i18n.t().subscriptionsSubtitle }}</p>

    @if (session.planTier() === 'FREE') {
      <div class="plan-usage-banner">
        <div class="plan-usage-meta">
          <span>{{ formatUsageHint(store.total(), 5) }}</span>
          <a routerLink="/billing/plans">{{ i18n.t().upgradeLink }}</a>
        </div>
        <div class="plan-usage-track">
          <div class="plan-usage-fill"
               [style.width.%]="usagePercent()"
               [class.usage-warn]="store.total() === 4"
               [class.usage-danger]="store.total() >= 5"></div>
        </div>
      </div>
    }

    <section class="card mb-3">
      <app-plan-gate *ngIf="quotaReached()" requiredPlan="PRO" />
      <div class="section-head" *ngIf="!quotaReached()">
        <div class="section-head-actions">
          <button *ngIf="canEdit()" pButton type="button" [label]="i18n.t().addSubscriptionBtn" icon="pi pi-plus" (click)="showCreateForm.set(true)"></button>
          <button *ngIf="canEdit()" pButton type="button" [label]="i18n.t().addFromLibraryBtn" icon="pi pi-th-large" class="p-button-outlined" (click)="showLibraryDialog.set(true)"></button>
          <button pButton type="button" [label]="i18n.t().importCsvBtn" icon="pi pi-upload" class="p-button-outlined" (click)="csvInput.click()"></button>
        </div>
      </div>
      <input #csvInput type="file" accept=".csv" class="hidden-input" (change)="importCsv($event)" />

      <!-- Library dialog -->
      <p-dialog
        [visible]="showLibraryDialog()"
        (visibleChange)="showLibraryDialog.set($event)"
        [modal]="true"
        [header]="i18n.t().templatePickerTitle"
        [style]="{ width: '760px', maxWidth: '96vw' }"
        [closable]="true"
      >
        <app-template-picker
          *ngIf="showLibraryDialog()"
          (added)="onLibraryAdded($event)"
          (addCustom)="showLibraryDialog.set(false); showCreateForm.set(true)"
        />
      </p-dialog>

      <div *ngIf="showCreateForm()">
        <h3 class="form-title">{{ i18n.t().createSubscriptionTitle }}</h3>
        <form [formGroup]="form" (ngSubmit)="createSubscription()" class="form-grid">
          <div class="field">
            <label>{{ i18n.t().fieldVendorName }}</label>
            <div class="field-control">
              <p-autoComplete
                formControlName="vendorName"
                [suggestions]="vendorSuggestionNames()"
                (completeMethod)="searchVendors($event)"
                [forceSelection]="false"
                [placeholder]="isInvalid('vendorName') ? i18n.t().vendorNameRequiredPlaceholder : 'Slack'"
                [class.input-inline-error]="isInvalid('vendorName')"
                styleClass="w-full"
                appendTo="body"
              ></p-autoComplete>
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
              <p-calendar formControlName="renewalDate" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" [class.input-inline-error]="isInvalid('renewalDate')" styleClass="w-full"></p-calendar>
            </div>
          </div>

          <div class="field">
            <label>
              {{ i18n.t().fieldNextPaymentDate }}
              <span class="label-meta">{{ form.controls.paymentMode.value === "MANUAL" ? i18n.t().labelRequiredForManual : i18n.t().labelOptionalForAutomatic }}</span>
            </label>
            <div class="field-control">
              <p-calendar formControlName="nextPaymentDate" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" [class.input-inline-error]="isInvalid('nextPaymentDate')" styleClass="w-full"></p-calendar>
            </div>
          </div>

          <div class="field span-2">
            <label>{{ i18n.t().fieldVendorUrlOptional }}</label>
            <input pInputText formControlName="vendorUrl" placeholder="https://vendor.com" />
          </div>

          <div class="field span-full">
            <label>{{ i18n.t().fieldTags }}</label>
            <p-chips formControlName="tags" [addOnBlur]="true" styleClass="w-full"></p-chips>
          </div>

          <div class="field span-full">
            <label>{{ i18n.t().fieldDescription }}</label>
            <textarea pInputTextarea formControlName="description" rows="2" class="w-full"></textarea>
          </div>

          <div class="field span-full">
            <label>{{ i18n.t().fieldNotes }}</label>
            <textarea pInputTextarea formControlName="notes" rows="2" class="w-full"></textarea>
          </div>

          <div class="field">
            <label>{{ i18n.t().fieldOwner }}</label>
            <p-dropdown [options]="ownerOptions()" formControlName="ownerId" optionLabel="label" optionValue="value" [showClear]="true" [placeholder]="'—'"></p-dropdown>
          </div>

          <div class="field">
            <label>{{ i18n.t().fieldDocumentUrl }}</label>
            <input pInputText formControlName="documentUrl" placeholder="https://..." />
          </div>

          <div class="field actions span-full form-actions-row">
            <button pButton type="submit" [label]="i18n.t().btnSaveSubscription" [loading]="saving()"></button>
            <button pButton type="button" [label]="i18n.t().msgCancelLabel" class="p-button-text" (click)="cancelCreate()"></button>
          </div>
        </form>
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
          <label class="zombie-filter-label">
            <input
              type="checkbox"
              [ngModel]="zombieOnlyFilter()"
              (ngModelChange)="onZombieFilterChange($event)"
            />
            {{ i18n.t().zombieOnlyFilter }}
          </label>
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
            <th>{{ i18n.t().lastUsedLabel }}</th>
            <th class="actions-col">{{ i18n.t().thActions }}</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-sub>
          <tr [class.zombie-row]="sub.isZombie">
            <td>
              <div class="vendor-cell">
                <div class="vendor-name-row">
                  <span>{{ sub.vendorName }}</span>
                  <span *ngIf="sub.isZombie" class="zombie-badge">{{ i18n.t().zombieBadge }}</span>
                </div>
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
            <td>
              <span *ngIf="lastUsedDaysSince(sub.lastUsedAt) !== null; else noLastUsed"
                    [class.last-used-ok]="lastUsedDaysSince(sub.lastUsedAt)! < 30"
                    [class.last-used-warn]="lastUsedDaysSince(sub.lastUsedAt)! >= 30 && lastUsedDaysSince(sub.lastUsedAt)! < 60"
                    [class.last-used-danger]="lastUsedDaysSince(sub.lastUsedAt)! >= 60">
                {{ lastUsedDaysSince(sub.lastUsedAt) }}d ago
              </span>
              <ng-template #noLastUsed><span class="last-used-none">—</span></ng-template>
            </td>
            <td class="actions-col">
              <button
                *ngIf="sub.isZombie && canEdit()"
                pButton
                type="button"
                class="p-button-sm p-button-warning p-button-text"
                icon="pi pi-check"
                [loading]="markingUsedId() === sub.id"
                (click)="markAsUsed(sub)"
                [label]="i18n.t().markAsUsedBtn"
              ></button>
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
                *ngIf="canEdit()"
                pButton
                type="button"
                class="p-button-sm p-button-secondary p-button-text"
                icon="pi pi-pencil"
                (click)="openEdit(sub)"
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
            <td colspan="7">
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

    <!-- Edit subscription dialog -->
    <p-dialog
      [header]="i18n.t().editSubscriptionTitle"
      [(visible)]="editDialogVisible"
      [modal]="true"
      [style]="{ width: '680px', maxWidth: '95vw' }"
      [draggable]="false"
      [resizable]="false"
    >
      <form [formGroup]="editForm" (ngSubmit)="saveEdit()" class="form-grid" style="margin-top: 8px;">
        <div class="field">
          <label>{{ i18n.t().fieldVendorName }}</label>
          <p-autoComplete
            formControlName="vendorName"
            [suggestions]="vendorSuggestionNames()"
            (completeMethod)="searchVendors($event)"
            [forceSelection]="false"
            [placeholder]="isEditInvalid('vendorName') ? i18n.t().vendorNameRequiredPlaceholder : 'Slack'"
            [class.input-inline-error]="isEditInvalid('vendorName')"
            styleClass="w-full"
            appendTo="body"
          ></p-autoComplete>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldCategory }}</label>
          <p-autoComplete
            formControlName="category"
            [suggestions]="filteredCategories"
            (completeMethod)="filterCategories($event)"
            [forceSelection]="false"
            [dropdown]="true"
            [placeholder]="isEditInvalid('category') ? i18n.t().categoryRequiredPlaceholder : i18n.t().selectOrTypeCategoryPlaceholder"
            [class.input-inline-error]="isEditInvalid('category')"
            styleClass="w-full"
          ></p-autoComplete>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldAmount }}</label>
          <input
            pInputText
            formControlName="amount"
            [placeholder]="isEditInvalid('amount') ? i18n.t().amountRequiredPlaceholder : '120.00'"
            [class.input-inline-error]="isEditInvalid('amount')"
          />
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
          <label [class.field-label-error]="isEditInvalid('renewalDate')">
            {{ i18n.t().fieldRenewalDate }}
            <span class="label-inline-error" *ngIf="isEditInvalid('renewalDate') && !editForm.controls.renewalDate.value">{{ i18n.t().fieldRequired }}</span>
          </label>
          <p-calendar formControlName="renewalDate" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" [class.input-inline-error]="isEditInvalid('renewalDate')" styleClass="w-full"></p-calendar>
        </div>

        <div class="field">
          <label>
            {{ i18n.t().fieldNextPaymentDate }}
            <span class="label-meta">{{ editForm.controls.paymentMode.value === "MANUAL" ? i18n.t().labelRequiredForManual : i18n.t().labelOptionalForAutomatic }}</span>
          </label>
          <p-calendar formControlName="nextPaymentDate" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" [class.input-inline-error]="isEditInvalid('nextPaymentDate')" styleClass="w-full"></p-calendar>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldStatus }}</label>
          <p-dropdown [options]="statusOptions()" formControlName="status" optionLabel="label" optionValue="value"></p-dropdown>
        </div>

        <div class="field span-2">
          <label>{{ i18n.t().fieldVendorUrlOptional }}</label>
          <input pInputText formControlName="vendorUrl" placeholder="https://vendor.com" />
        </div>

        <div class="field span-full">
          <label>{{ i18n.t().fieldTags }}</label>
          <p-chips formControlName="tags" [addOnBlur]="true" styleClass="w-full"></p-chips>
        </div>

        <div class="field span-full">
          <label>{{ i18n.t().fieldDescription }}</label>
          <textarea pInputTextarea formControlName="description" rows="2" class="w-full"></textarea>
        </div>

        <div class="field span-full">
          <label>{{ i18n.t().fieldNotes }}</label>
          <textarea pInputTextarea formControlName="notes" rows="2" class="w-full"></textarea>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldOwner }}</label>
          <p-dropdown [options]="ownerOptions()" formControlName="ownerId" optionLabel="label" optionValue="value" [showClear]="true" [placeholder]="'—'"></p-dropdown>
        </div>

        <div class="field">
          <label>{{ i18n.t().fieldDocumentUrl }}</label>
          <input pInputText formControlName="documentUrl" placeholder="https://..." />
        </div>
      </form>

      <ng-template pTemplate="footer">
        <button pButton type="button" [label]="i18n.t().msgCancelLabel" class="p-button-text" (click)="editDialogVisible.set(false)"></button>
        <button pButton type="button" [label]="i18n.t().btnUpdateSubscription" [loading]="editSaving()" (click)="saveEdit()"></button>
      </ng-template>
    </p-dialog>
  `,
  styles: [
    `
      .form-title {
        margin: 12px 0 10px;
        font-size: 1rem;
        color: #334155;
      }
      .section-head-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
      }
      .form-actions-row {
        display: flex;
        gap: 8px;
        align-items: center;
      }
      .span-full {
        grid-column: 1 / -1;
      }
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
      .zombie-row {
        background: #fff9f0;
      }
      .vendor-name-row {
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .zombie-badge {
        display: inline-block;
        background: #f97316;
        color: #fff;
        font-size: 0.7rem;
        font-weight: 600;
        padding: 1px 6px;
        border-radius: 10px;
        white-space: nowrap;
      }
      .last-used-ok {
        color: #16a34a;
        font-size: 0.85rem;
      }
      .last-used-warn {
        color: #ca8a04;
        font-size: 0.85rem;
      }
      .last-used-danger {
        color: #dc2626;
        font-size: 0.85rem;
        font-weight: 600;
      }
      .last-used-none {
        color: #94a3b8;
        font-size: 0.85rem;
      }
      .zombie-filter-label {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 0.9rem;
        color: #475569;
        cursor: pointer;
        white-space: nowrap;
      }
      .plan-usage-banner {
        margin-bottom: 12px;
      }
      .plan-usage-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.85rem;
        color: #475569;
        margin-bottom: 4px;
      }
      .plan-usage-meta a {
        color: var(--primary-color, #6366f1);
        text-decoration: none;
        font-size: 0.8rem;
      }
      .plan-usage-track {
        height: 6px;
        background: #e2e8f0;
        border-radius: 4px;
        overflow: hidden;
      }
      .plan-usage-fill {
        height: 100%;
        background: #22c55e;
        border-radius: 4px;
        transition: width 0.3s ease;
      }
      .plan-usage-fill.usage-warn {
        background: #f59e0b;
      }
      .plan-usage-fill.usage-danger {
        background: #ef4444;
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
  protected readonly session = inject(AuthSessionService);
  private readonly messageService = inject(MessageService, { optional: true });
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly showCreateForm = signal(false);
  readonly showLibraryDialog = signal(false);
  readonly saving = signal(false);
  readonly quotaReached = signal(false);
  readonly markingPaidId = signal<string | null>(null);
  readonly exportingFormat = signal<"csv" | "pdf" | null>(null);
  readonly vendorSearch = signal("");
  readonly categoryFilter = signal("");
  readonly statusFilter = signal("");
  readonly zombieOnlyFilter = signal(false);
  readonly markingUsedId = signal<string | null>(null);

  // Edit dialog state
  readonly editDialogVisible = signal(false);
  readonly editTarget = signal<SubscriptionItem | null>(null);
  readonly editSaving = signal(false);

  readonly canEdit = computed(() => this.session.currentUserRole() !== "VIEWER");
  readonly usagePercent = computed(() => Math.min(100, Math.round((this.store.total() / 5) * 100)));

  formatUsageHint(current: number, max: number): string {
    return this.i18n.t().planUsageHint.replace("{0}", String(current)).replace("{1}", String(max));
  }

  // Vendor autocomplete
  readonly vendorSuggestions = signal<VendorSuggestion[]>([]);
  readonly vendorSuggestionNames = computed(() => this.vendorSuggestions().map((s) => s.vendorName));
  private readonly vendorQuery$ = new Subject<string>();

  // Owner dropdown
  readonly ownerOptions = signal<{ label: string; value: string }[]>([]);

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
    renewalDate: [null as Date | null, [Validators.required]],
    nextPaymentDate: [null as Date | null],
    vendorUrl: [""],
    tags: [[] as string[]],
    description: [""],
    notes: [""],
    ownerId: [null as string | null],
    documentUrl: [""]
  });

  readonly editForm = this.fb.group({
    vendorName: ["", [Validators.required]],
    category: ["", [Validators.required]],
    amount: ["", [Validators.required]],
    currency: ["USD", [Validators.required]],
    billingCycle: ["MONTHLY", [Validators.required]],
    paymentMode: ["AUTO", [Validators.required]],
    renewalDate: [null as Date | null, [Validators.required]],
    nextPaymentDate: [null as Date | null],
    vendorUrl: [""],
    status: ["ACTIVE", [Validators.required]],
    tags: [[] as string[]],
    description: [""],
    notes: [""],
    ownerId: [null as string | null],
    documentUrl: [""]
  });

  hasActiveFilters(): boolean {
    return !!(this.vendorSearch() || this.categoryFilter() || this.statusFilter() || this.zombieOnlyFilter());
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

    this.api.getMembers().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ items }) => {
      this.ownerOptions.set(items.map((m) => ({ label: m.name || m.email, value: m.id })));
    });

    this.vendorSearch$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((vendor) => {
        this.store.load({ vendor, page: 1 });
      });

    this.vendorQuery$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => {
        if (q.length < 2) {
          this.vendorSuggestions.set([]);
          return;
        }
        this.api.getVendorSuggestions(q).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
          this.vendorSuggestions.set(res.items);
        });
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

    this.editForm.controls.paymentMode.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((mode) => {
      const nextPayment = this.editForm.controls.nextPaymentDate;
      if (mode === "MANUAL") {
        nextPayment.setValidators([Validators.required]);
        if (!nextPayment.value) {
          nextPayment.setValue(this.editForm.controls.renewalDate.value);
        }
      } else {
        nextPayment.clearValidators();
      }
      nextPayment.updateValueAndValidity({ emitEvent: false });
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

  onZombieFilterChange(checked: boolean): void {
    this.zombieOnlyFilter.set(checked);
    this.store.load({ zombie: checked ? true : null, page: 1 });
  }

  markAsUsed(subscription: SubscriptionItem): void {
    const t = this.i18n.t();
    this.markingUsedId.set(subscription.id);
    this.store.markUsed(subscription.id).then(() => {
      this.messageService?.add({
        severity: "success",
        summary: t.msgMarkedAsUsed,
        detail: subscription.vendorName
      });
      this.markingUsedId.set(null);
    }).catch(() => {
      this.messageService?.add({
        severity: "error",
        summary: t.msgMarkUsedFailed,
        detail: subscription.vendorName
      });
      this.markingUsedId.set(null);
    });
  }

  lastUsedDaysSince(lastUsedAt: string | null | undefined): number | null {
    if (!lastUsedAt) return null;
    const ms = Date.now() - new Date(lastUsedAt).getTime();
    return Math.floor(ms / (24 * 60 * 60 * 1000));
  }

  onPageChange(event: { first?: number; rows?: number; page?: number }): void {
    this.store.load({ page: (event.page ?? 0) + 1, size: event.rows ?? this.store.size() });
  }

  filterCategories(event: AutoCompleteCompleteEvent): void {
    const query = event.query.toLowerCase();
    this.filteredCategories = this.allCategories.filter((c) => c.includes(query));
  }

  searchVendors(event: AutoCompleteCompleteEvent): void {
    this.vendorQuery$.next(event.query);
  }

  cancelCreate(): void {
    this.showCreateForm.set(false);
    this.form.reset({ currency: "USD", billingCycle: "MONTHLY", paymentMode: "AUTO", tags: [] });
  }

  private dateToIso(v: Date | string | null | undefined): string | null {
    if (!v) return null;
    if (v instanceof Date) {
      const y = v.getFullYear();
      const m = String(v.getMonth() + 1).padStart(2, "0");
      const d = String(v.getDate()).padStart(2, "0");
      return `${y}-${m}-${d}`;
    }
    const s = String(v).trim();
    return s || null;
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
        renewalDate: this.dateToIso(payload.renewalDate),
        autoRenews: payload.paymentMode !== "MANUAL",
        paymentMode: payload.paymentMode,
        nextPaymentDate: this.dateToIso(payload.nextPaymentDate),
        status: "ACTIVE",
        tags: payload.tags ?? [],
        description: payload.description?.trim() || null,
        notes: payload.notes?.trim() || null,
        ownerId: payload.ownerId || null,
        documentUrl: payload.documentUrl?.trim() || null
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.form.reset({ currency: "USD", billingCycle: "MONTHLY", paymentMode: "AUTO", tags: [] });
          this.showCreateForm.set(false);
          this.store.load({ page: 1 });
          this.messageService?.add({
            severity: "success",
            summary: t.msgSubscriptionSaved,
            detail: t.msgSubscriptionSavedDetail
          });
          if (created.duplicateWarnings?.length) {
            this.messageService?.add({
              severity: "warn",
              summary: t.duplicateSimilarFound,
              detail: t.duplicateSimilarDetail
            });
          }
          this.saving.set(false);
        },
        error: (err) => {
          if (err?.status === 403 && err?.error?.reason === "QUOTA_EXCEEDED") {
            this.quotaReached.set(true);
            this.showCreateForm.set(false);
          } else {
            this.messageService?.add({
              severity: "error",
              summary: t.msgSaveFailed,
              detail: t.msgSaveFailedDetail
            });
          }
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

  onLibraryAdded(result: BatchCreateResponse): void {
    this.showLibraryDialog.set(false);
    this.store.load({ page: 1 });
    const t = this.i18n.t();
    const summary = t.templatePickerAdded.replace("{0}", String(result.created));
    const skippedMsg = result.skipped > 0
      ? " " + t.templatePickerSkipped.replace("{0}", String(result.skipped))
      : "";
    this.messageService?.add({
      severity: result.skipped > 0 ? "warn" : "success",
      summary,
      detail: skippedMsg || undefined,
      life: 5000
    });
    if (result.skipped > 0) {
      this.quotaReached.set(true);
    }
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
        error: (err) => {
          if (err?.status === 403 && err?.error?.reason === "QUOTA_EXCEEDED") {
            this.quotaReached.set(true);
          } else {
            const t = this.i18n.t();
            this.messageService?.add({
              severity: "error",
              summary: t.msgCsvImportFailed,
              detail: t.msgCsvImportFailedDetail
            });
          }
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

  isInvalid(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  isEditInvalid(controlName: keyof typeof this.editForm.controls): boolean {
    const control = this.editForm.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  openEdit(sub: SubscriptionItem): void {
    this.editTarget.set(sub);
    this.editForm.reset({
      vendorName: sub.vendorName,
      category: sub.category,
      amount: sub.amount,
      currency: sub.currency,
      billingCycle: sub.billingCycle,
      paymentMode: sub.paymentMode,
      renewalDate: sub.renewalDate ? this.isoToDate(sub.renewalDate) : null,
      nextPaymentDate: sub.nextPaymentDate ? this.isoToDate(sub.nextPaymentDate) : null,
      vendorUrl: sub.vendorUrl ?? "",
      status: sub.status,
      tags: sub.tags ?? [],
      description: sub.description ?? "",
      notes: sub.notes ?? "",
      ownerId: sub.ownerId ?? null,
      documentUrl: sub.documentUrl ?? ""
    });
    this.editDialogVisible.set(true);
  }

  private isoToDate(iso: string): Date {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, (m ?? 1) - 1, d ?? 1);
  }

  saveEdit(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const target = this.editTarget();
    if (!target) return;

    const payload = this.editForm.getRawValue();
    const t = this.i18n.t();
    this.editSaving.set(true);

    this.store.update(target.id, {
      vendorName: payload.vendorName?.trim(),
      vendorUrl: payload.vendorUrl?.trim() || null,
      category: payload.category?.trim(),
      amount: payload.amount?.trim(),
      currency: payload.currency,
      billingCycle: payload.billingCycle,
      renewalDate: this.dateToIso(payload.renewalDate),
      autoRenews: payload.paymentMode !== "MANUAL",
      paymentMode: payload.paymentMode,
      nextPaymentDate: this.dateToIso(payload.nextPaymentDate),
      status: payload.status,
      tags: payload.tags ?? [],
      description: payload.description?.trim() || null,
      notes: payload.notes?.trim() || null,
      ownerId: payload.ownerId || null,
      documentUrl: payload.documentUrl?.trim() || null
    }).then((updated) => {
      this.editDialogVisible.set(false);
      this.editSaving.set(false);
      this.messageService?.add({
        severity: "success",
        summary: t.msgSubscriptionUpdated,
        detail: t.msgSubscriptionUpdatedDetail
      });
      if (updated.duplicateWarnings?.length) {
        this.messageService?.add({
          severity: "warn",
          summary: t.duplicateSimilarFound,
          detail: t.duplicateSimilarDetail
        });
      }
    }).catch(() => {
      this.messageService?.add({
        severity: "error",
        summary: t.msgUpdateFailed,
        detail: t.msgUpdateFailedDetail
      });
      this.editSaving.set(false);
    });
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

  currency(amount: string, currencyCode: string): string {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currencyCode || "USD",
      maximumFractionDigits: 2
    }).format(Number(amount));
  }
}
