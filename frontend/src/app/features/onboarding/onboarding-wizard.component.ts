import { ChangeDetectionStrategy, Component, DestroyRef, EventEmitter, Output, computed, inject, signal } from "@angular/core";
import { NgIf, NgSwitch, NgSwitchCase } from "@angular/common";
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ButtonModule } from "primeng/button";
import { DialogModule } from "primeng/dialog";
import { DropdownModule } from "primeng/dropdown";
import { InputNumberModule } from "primeng/inputnumber";
import { InputTextModule } from "primeng/inputtext";
import { StepsModule } from "primeng/steps";
import { AutoCompleteModule, AutoCompleteCompleteEvent } from "primeng/autocomplete";
import { CalendarModule } from "primeng/calendar";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";
import { TemplatePickerComponent } from "../../shared/components/template-picker.component";
import { BatchCreateResponse } from "../../shared/models";

@Component({
  selector: "app-onboarding-wizard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgIf, NgSwitch, NgSwitchCase, FormsModule, ReactiveFormsModule, ButtonModule, DialogModule, DropdownModule, InputNumberModule, InputTextModule, StepsModule, AutoCompleteModule, CalendarModule, TemplatePickerComponent],
  template: `
    <p-dialog
      [visible]="true"
      [modal]="true"
      [closable]="false"
      [header]="i18n.t().onboardingTitle"
      [style]="{ width: '700px', maxWidth: '96vw' }"
    >
      <p-steps [model]="steps()" [activeIndex]="activeStep()" [readonly]="true" styleClass="ob-steps" />

      <div class="ob-body" [ngSwitch]="activeStep()">
        <!-- Step 0: First Subscription — template picker or manual form -->
        <div *ngSwitchCase="0" class="ob-step">
          <ng-container *ngIf="ob0Mode() === 'picker'">
            <h3>{{ i18n.t().ob0Title }}</h3>
            <p>{{ i18n.t().ob0Body }}</p>
            <app-template-picker
              (added)="onTemplatesAdded($event)"
              (addCustom)="ob0Mode.set('form')"
            />
          </ng-container>

          <ng-container *ngIf="ob0Mode() === 'form'">
            <h3>{{ i18n.t().ob0Title }}</h3>
            <p class="ob-hint">{{ i18n.t().ob0FormHint }}</p>
            <form [formGroup]="ob0Form" class="ob-form-grid">
              <div class="ob-field">
                <label>{{ i18n.t().fieldVendorName }}</label>
                <input
                  pInputText
                  formControlName="vendorName"
                  [placeholder]="i18n.t().fieldVendorName"
                  [class.input-error]="ob0Invalid('vendorName')"
                />
              </div>
              <div class="ob-field">
                <label>{{ i18n.t().fieldCategory }}</label>
                <p-autoComplete
                  formControlName="category"
                  [suggestions]="ob0FilteredCategories"
                  (completeMethod)="ob0FilterCategories($event)"
                  [forceSelection]="false"
                  [dropdown]="true"
                  [placeholder]="i18n.t().selectOrTypeCategoryPlaceholder"
                  [class.input-error]="ob0Invalid('category')"
                  styleClass="w-full"
                ></p-autoComplete>
              </div>
              <div class="ob-field">
                <label>{{ i18n.t().fieldAmount }}</label>
                <input
                  pInputText
                  formControlName="amount"
                  placeholder="120.00"
                  [class.input-error]="ob0Invalid('amount')"
                />
              </div>
              <div class="ob-field">
                <label>{{ i18n.t().fieldCurrency }}</label>
                <p-dropdown [options]="currencies" formControlName="currency" optionLabel="label" optionValue="value"></p-dropdown>
              </div>
              <div class="ob-field">
                <label>{{ i18n.t().fieldBillingCycle }}</label>
                <p-dropdown [options]="cycles()" formControlName="billingCycle" optionLabel="label" optionValue="value"></p-dropdown>
              </div>
              <div class="ob-field">
                <label>{{ i18n.t().fieldRenewalDate }}</label>
                <p-calendar formControlName="renewalDate" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" styleClass="w-full" [class.input-error]="ob0Invalid('renewalDate')"></p-calendar>
              </div>
            </form>
            <p class="ob-error" *ngIf="ob0Error()">{{ ob0Error() }}</p>
          </ng-container>
        </div>

        <!-- Step 1: Invite Team -->
        <div *ngSwitchCase="1" class="ob-step">
          <h3>{{ i18n.t().ob1Title }}</h3>
          <p>{{ i18n.t().ob1Body }}</p>
          <div class="ob-invite-row">
            <input
              pInputText
              type="email"
              placeholder="teammate@company.com"
              [(ngModel)]="inviteEmail"
              class="ob-email-input"
            />
            <p-dropdown
              [options]="roleOptions()"
              [(ngModel)]="inviteRole"
              optionLabel="label"
              optionValue="value"
              placeholder="Role"
            />
          </div>
          <p class="ob-error" *ngIf="inviteError()">{{ inviteError() }}</p>
          <p class="ob-success" *ngIf="inviteSuccess()">{{ i18n.t().invitationSentSuccess }}</p>
        </div>

        <!-- Step 2: Set Budget -->
        <div *ngSwitchCase="2" class="ob-step">
          <h3>{{ i18n.t().ob2Title }}</h3>
          <p>{{ i18n.t().ob2Body }}</p>
          <p-inputNumber
            [(ngModel)]="budget"
            mode="currency"
            currency="USD"
            [min]="0"
            [maxFractionDigits]="0"
            placeholder="e.g. 5000"
            styleClass="ob-budget-input"
          />
          <div class="ob-digest-row">
            <input type="checkbox" id="ob-digest-chk" [(ngModel)]="digestEnabled" class="ob-digest-chk" />
            <label for="ob-digest-chk" class="ob-digest-label">{{ i18n.t().digestEnabledLabel }}</label>
          </div>
          <p class="ob-digest-hint">{{ i18n.t().digestEnabledHint }}</p>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <div class="ob-footer">
          <p-button
            [label]="i18n.t().btnSkipStep"
            [text]="true"
            severity="secondary"
            [disabled]="saving()"
            (click)="skipStep()"
          />
          <!-- In step 0 picker mode the TemplatePicker has its own button; show Next only in form mode or steps 1/2 -->
          <p-button
            *ngIf="activeStep() !== 0 || ob0Mode() === 'form'"
            [label]="activeStep() < steps().length - 1 ? i18n.t().btnNext : i18n.t().btnFinish"
            [loading]="saving()"
            (click)="completeStep()"
          />
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: [
    `
      .ob-body {
        min-height: 130px;
        padding: 20px 0 8px;
      }
      .ob-step h3 {
        margin: 0 0 8px;
        font-size: 1.1rem;
      }
      .ob-step p {
        margin: 0 0 14px;
        color: #475569;
        line-height: 1.5;
      }
      .ob-hint {
        font-size: 0.88rem;
        color: #475569;
        margin: 0 0 12px;
      }
      .ob-form-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 10px;
      }
      .ob-field {
        display: grid;
        gap: 4px;
      }
      .ob-field label {
        font-size: 0.85rem;
        color: #475569;
      }
      .ob-field :is(input, .p-dropdown) {
        width: 100%;
      }
      .input-error {
        border-color: #ef4444 !important;
      }
      .ob-invite-row {
        display: flex;
        gap: 10px;
        align-items: center;
        flex-wrap: wrap;
      }
      .ob-email-input {
        flex: 1;
        min-width: 160px;
      }
      .ob-error {
        color: #dc2626;
        font-size: 0.88rem;
        margin: 8px 0 0;
      }
      .ob-success {
        color: #16a34a;
        font-size: 0.88rem;
        margin: 8px 0 0;
      }
      .ob-footer {
        display: flex;
        justify-content: flex-end;
        gap: 10px;
        width: 100%;
      }
      .ob-digest-row {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 14px;
      }
      .ob-digest-chk {
        width: 16px;
        height: 16px;
        accent-color: #6366f1;
        cursor: pointer;
        flex-shrink: 0;
      }
      .ob-digest-label {
        font-size: 0.9rem;
        font-weight: 500;
        color: #0f172a;
        cursor: pointer;
      }
      .ob-digest-hint {
        font-size: 0.8rem;
        color: #64748b;
        margin: 4px 0 0 24px;
        line-height: 1.4;
      }
      @media (max-width: 768px) {
        .ob-invite-row {
          flex-direction: column;
          align-items: stretch;
        }
        .ob-email-input {
          width: 100%;
        }
      }
    `
  ]
})
export class OnboardingWizardComponent {
  @Output() wizardCompleted = new EventEmitter<void>();

  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly activeStep = signal(0);
  readonly saving = signal(false);
  readonly inviteError = signal<string | null>(null);
  readonly inviteSuccess = signal(false);

  // Step 0 — 'picker' shows TemplatePicker, 'form' shows manual form
  readonly ob0Mode = signal<"picker" | "form">("picker");
  readonly ob0Error = signal<string | null>(null);
  ob0AllCategories: string[] = [];
  ob0FilteredCategories: string[] = [];

  readonly currencies = [
    { label: "USD", value: "USD" },
    { label: "EUR", value: "EUR" },
    { label: "GBP", value: "GBP" }
  ];

  readonly ob0Form = this.fb.group({
    vendorName: ["", [Validators.required]],
    category: ["", [Validators.required]],
    amount: ["", [Validators.required]],
    currency: ["USD", [Validators.required]],
    billingCycle: ["MONTHLY", [Validators.required]],
    renewalDate: [null as Date | null, [Validators.required]]
  });

  inviteEmail = "";
  inviteRole: "ADMIN" | "EDITOR" | "VIEWER" = "VIEWER";
  budget: number | null = null;
  digestEnabled = true;

  readonly steps = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.stepFirstSubscription },
      { label: t.stepInviteTeam },
      { label: t.stepSetBudget }
    ];
  });

  readonly cycles = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.cycleMonthly, value: "MONTHLY" },
      { label: t.cycleQuarterly, value: "QUARTERLY" },
      { label: t.cycleAnnual, value: "ANNUAL" }
    ];
  });

  readonly roleOptions = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.roleViewer, value: "VIEWER" },
      { label: t.roleEditor, value: "EDITOR" },
      { label: t.roleAdmin, value: "ADMIN" }
    ];
  });

  private readonly completedSteps: string[] = [];

  constructor() {
    this.api.getCategories().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ predefined, custom }) => {
      this.ob0AllCategories = [...predefined, ...custom];
    });
  }

  /** Called when TemplatePicker successfully adds subscriptions. */
  onTemplatesAdded(result: BatchCreateResponse): void {
    if (result.created > 0) {
      this.completedSteps.push("subscription");
      this.patchProgress();
    }
    this.advance();
  }

  ob0FilterCategories(event: AutoCompleteCompleteEvent): void {
    const query = event.query.toLowerCase();
    this.ob0FilteredCategories = this.ob0AllCategories.filter((c) => c.includes(query));
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

  ob0Invalid(field: "vendorName" | "category" | "amount" | "renewalDate"): boolean {
    const ctrl = this.ob0Form.controls[field];
    return ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  skipStep(): void {
    this.advance();
  }

  completeStep(): void {
    const step = this.activeStep();
    if (step === 0 && this.ob0Mode() === "form") {
      this.createFirstSubscriptionAndAdvance();
    } else if (step === 1 && this.inviteEmail.trim()) {
      this.sendInviteAndAdvance();
    } else if (step === 2) {
      this.saveBudgetAndFinish();
    } else {
      this.patchProgress();
      this.advance();
    }
  }

  private createFirstSubscriptionAndAdvance(): void {
    if (this.ob0Form.invalid) {
      this.ob0Form.markAllAsTouched();
      return;
    }
    const v = this.ob0Form.getRawValue();
    this.saving.set(true);
    this.ob0Error.set(null);
    this.api.createSubscription({
      vendorName: v.vendorName?.trim(),
      category: v.category?.trim(),
      amount: v.amount?.trim(),
      currency: v.currency,
      billingCycle: v.billingCycle,
      renewalDate: this.dateToIso(v.renewalDate),
      autoRenews: true,
      paymentMode: "AUTO",
      status: "ACTIVE",
      tags: []
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.completedSteps.push("subscription");
        this.patchProgress();
        this.advance();
      },
      error: (err: { error?: { message?: string } }) => {
        this.saving.set(false);
        this.ob0Error.set(err?.error?.message ?? "Failed to save subscription. Check input.");
      }
    });
  }

  private advance(): void {
    const next = this.activeStep() + 1;
    if (next >= this.steps().length) {
      this.finish();
    } else {
      this.inviteError.set(null);
      this.inviteSuccess.set(false);
      this.activeStep.set(next);
    }
  }

  private sendInviteAndAdvance(): void {
    this.saving.set(true);
    this.inviteError.set(null);
    this.api
      .inviteMember({ email: this.inviteEmail.trim(), role: this.inviteRole })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.inviteSuccess.set(true);
          this.saving.set(false);
          this.completedSteps.push("invite");
          this.patchProgress();
          this.advance();
        },
        error: (err: { error?: { message?: string } }) => {
          this.saving.set(false);
          this.inviteError.set(err?.error?.message ?? this.i18n.t().ob1FailedMsg);
        }
      });
  }

  private saveBudgetAndFinish(): void {
    this.saving.set(true);
    this.completedSteps.push("budget");
    this.api
      .updateDigestSettings({ weeklyDigestEnabled: this.digestEnabled })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => {} });
    this.api
      .updateCompany({
        monthlyBudget: this.budget != null ? String(this.budget) : undefined,
        onboarding: { completed: true, completedSteps: this.completedSteps }
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.wizardCompleted.emit();
        },
        error: () => {
          this.saving.set(false);
          this.wizardCompleted.emit();
        }
      });
  }

  private finish(): void {
    this.saving.set(true);
    this.api
      .updateCompany({ onboarding: { completed: true, completedSteps: this.completedSteps } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.wizardCompleted.emit();
        },
        error: () => {
          this.saving.set(false);
          this.wizardCompleted.emit();
        }
      });
  }

  private patchProgress(): void {
    this.api
      .updateCompany({ onboarding: { completed: false, completedSteps: this.completedSteps } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => {} });
  }
}
