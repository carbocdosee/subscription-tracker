import { ChangeDetectionStrategy, Component, DestroyRef, EventEmitter, Output, computed, inject, signal } from "@angular/core";
import { NgIf, NgSwitch, NgSwitchCase } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ButtonModule } from "primeng/button";
import { DialogModule } from "primeng/dialog";
import { DropdownModule } from "primeng/dropdown";
import { InputNumberModule } from "primeng/inputnumber";
import { InputTextModule } from "primeng/inputtext";
import { StepsModule } from "primeng/steps";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  selector: "app-onboarding-wizard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgIf, NgSwitch, NgSwitchCase, FormsModule, ButtonModule, DialogModule, DropdownModule, InputNumberModule, InputTextModule, StepsModule],
  template: `
    <p-dialog
      [visible]="true"
      [modal]="true"
      [closable]="false"
      [header]="i18n.t().onboardingTitle"
      [style]="{ width: '640px', maxWidth: '96vw' }"
    >
      <p-steps [model]="steps()" [activeIndex]="activeStep()" [readonly]="true" styleClass="ob-steps" />

      <div class="ob-body" [ngSwitch]="activeStep()">
        <!-- Step 0: First Subscription -->
        <div *ngSwitchCase="0" class="ob-step">
          <h3>{{ i18n.t().ob0Title }}</h3>
          <p>{{ i18n.t().ob0Body }}</p>
          <p class="ob-hint" [innerHTML]="i18n.t().ob0Hint"></p>
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
          <p-button
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
        background: #eff6ff;
        border: 1px solid #bfdbfe;
        border-radius: 8px;
        padding: 10px 12px;
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

  readonly activeStep = signal(0);
  readonly saving = signal(false);
  readonly inviteError = signal<string | null>(null);
  readonly inviteSuccess = signal(false);

  inviteEmail = "";
  inviteRole: "ADMIN" | "EDITOR" | "VIEWER" = "VIEWER";
  budget: number | null = null;

  readonly steps = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.stepFirstSubscription },
      { label: t.stepInviteTeam },
      { label: t.stepSetBudget }
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

  skipStep(): void {
    this.advance();
  }

  completeStep(): void {
    const step = this.activeStep();
    if (step === 1 && this.inviteEmail.trim()) {
      this.sendInviteAndAdvance();
    } else if (step === 2) {
      this.saveBudgetAndFinish();
    } else {
      if (step === 0) this.completedSteps.push("subscription");
      this.patchProgress();
      this.advance();
    }
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
