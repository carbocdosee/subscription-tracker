import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CardModule } from "primeng/card";
import { InputTextModule } from "primeng/inputtext";
import { ButtonModule } from "primeng/button";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  selector: "app-forgot-password-page",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardModule, InputTextModule, ButtonModule],
  template: `
    <section class="auth-shell fade-in">
      <p-card styleClass="auth-card">
        <h1 class="auth-title">{{ i18n.t().forgotPasswordTitle }}</h1>
        <p class="auth-subtitle">{{ i18n.t().forgotPasswordSubtitle }}</p>

        @if (sent()) {
          <div class="success-box">{{ i18n.t().forgotPasswordSent }}</div>
          <a routerLink="/auth" class="back-link">{{ i18n.t().backToSignIn }}</a>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form">
            <label for="email">{{ i18n.t().fieldEmailAddress }}</label>
            <input id="email" pInputText type="email" formControlName="email" autocomplete="email" />

            <button pButton type="submit" [label]="i18n.t().forgotPasswordSendLink" [loading]="loading()"></button>
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
      .auth-form input {
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
export class ForgotPasswordPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly sent = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ["", [Validators.required, Validators.email]]
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.api
      .forgotPassword(this.form.getRawValue().email)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sent.set(true);
          this.loading.set(false);
        },
        error: () => {
          // Always show success to prevent email enumeration on the frontend too
          this.sent.set(true);
          this.loading.set(false);
        }
      });
  }
}
