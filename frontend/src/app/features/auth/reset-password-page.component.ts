import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CardModule } from "primeng/card";
import { PasswordModule } from "primeng/password";
import { ButtonModule } from "primeng/button";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  selector: "app-reset-password-page",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardModule, PasswordModule, ButtonModule],
  template: `
    <section class="auth-shell fade-in">
      <p-card styleClass="auth-card">
        <h1 class="auth-title">{{ i18n.t().resetPasswordTitle }}</h1>
        <p class="auth-subtitle">{{ i18n.t().resetPasswordSubtitle }}</p>

        @if (done()) {
          <div class="success-box">{{ i18n.t().resetPasswordDone }}</div>
          <a routerLink="/auth" class="back-link">{{ i18n.t().goToSignIn }}</a>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form">
            <label>{{ i18n.t().fieldNewPassword }}</label>
            <p-password
              formControlName="password"
              [toggleMask]="true"
              [feedback]="true"
              [inputStyle]="{ width: '100%' }"
              [style]="{ width: '100%' }"
            />
            @if (form.controls.password.touched && form.controls.password.hasError('minlength')) {
              <small class="field-error">{{ i18n.t().resetPasswordMinLength }}</small>
            }

            <button pButton type="submit" [label]="i18n.t().resetPasswordBtn" [loading]="loading()"></button>

            @if (tokenError()) {
              <div class="error-box">{{ tokenError() }}</div>
            }
          </form>

          <a routerLink="/auth" class="back-link">{{ i18n.t().backToSignIn }}</a>
        }
      </p-card>
    </section>
  `,
  styles: [
    `
      .auth-shell {
        min-height: calc(100vh - 40px);
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .auth-card {
        width: min(480px, 100%);
      }
      .auth-title {
        margin: 0;
      }
      .auth-subtitle {
        margin: 8px 0 18px 0;
        color: var(--muted);
      }
      .auth-form {
        display: grid;
        gap: 8px;
      }
      .auth-form label {
        color: #475569;
        margin-top: 6px;
      }
      .auth-form :is(.p-password) {
        width: 100%;
      }
      .auth-form button {
        margin-top: 10px;
      }
      .success-box {
        background: #f0fdf4;
        border: 1px solid #bbf7d0;
        border-radius: 8px;
        padding: 14px 16px;
        color: #166534;
        margin-bottom: 16px;
        font-size: 14px;
      }
      .error-box {
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 8px;
        padding: 12px 14px;
        color: #991b1b;
        font-size: 14px;
      }
      .field-error {
        color: #dc2626;
        font-size: 12px;
      }
      .back-link {
        display: inline-block;
        margin-top: 14px;
        color: #0c4a6e;
        text-decoration: none;
        font-size: 14px;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResetPasswordPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly done = signal(false);
  readonly tokenError = signal<string | null>(null);

  private readonly token = this.route.snapshot.queryParamMap.get("token") ?? "";

  readonly form = this.fb.nonNullable.group({
    password: ["", [Validators.required, Validators.minLength(10)]]
  });

  constructor() {
    if (!this.token) {
      void this.router.navigateByUrl("/auth");
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.tokenError.set(null);
    this.api
      .resetPassword(this.token, this.form.getRawValue().password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.done.set(true);
          this.loading.set(false);
        },
        error: (err: { error?: { message?: string } }) => {
          this.tokenError.set(err?.error?.message ?? this.i18n.t().resetPasswordTokenError);
          this.loading.set(false);
        }
      });
  }
}
