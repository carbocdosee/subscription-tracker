import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CardModule } from "primeng/card";
import { TabViewModule } from "primeng/tabview";
import { InputTextModule } from "primeng/inputtext";
import { PasswordModule } from "primeng/password";
import { ButtonModule } from "primeng/button";
import { CheckboxModule } from "primeng/checkbox";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { AuthSessionService } from "../../core/services/auth-session.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  selector: "app-auth-page",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardModule,
    TabViewModule,
    InputTextModule,
    PasswordModule,
    ButtonModule,
    CheckboxModule
  ],
  template: `
    <section class="auth-shell fade-in">
      <p-card styleClass="auth-card">
        <h1 class="auth-title">{{ i18n.t().authHeroTitle }}</h1>
        <p class="auth-subtitle">{{ i18n.t().authSubtitle }}</p>

        <p-tabView [(activeIndex)]="activeTab">
          <p-tabPanel [header]="i18n.t().tabLogin">
            <form [formGroup]="loginForm" (ngSubmit)="login()" class="auth-form">
              <label>{{ i18n.t().fieldEmail }}</label>
              <input pInputText formControlName="email" autocomplete="email" />

              <label>{{ i18n.t().fieldPassword }}</label>
              <p-password
                formControlName="password"
                [toggleMask]="true"
                [feedback]="false"
                [inputStyle]="{ width: '100%' }"
                [style]="{ width: '100%' }"
              />

              <button pButton type="submit" [label]="i18n.t().btnSignIn" [loading]="loginLoading()"></button>
              <a routerLink="/forgot-password" class="forgot-link">{{ i18n.t().forgotPassword }}</a>
            </form>
          </p-tabPanel>

          <p-tabPanel [header]="i18n.t().tabRegister">
            <form [formGroup]="registerForm" (ngSubmit)="register()" class="auth-form">
              <label>{{ i18n.t().fieldCompanyName }}</label>
              <input pInputText formControlName="companyName" autocomplete="organization" />

              <label>{{ i18n.t().fieldCompanyDomain }}</label>
              <input pInputText formControlName="companyDomain" placeholder="example.com" />

              <label>{{ i18n.t().fieldYourName }}</label>
              <input pInputText formControlName="fullName" autocomplete="name" />

              <label>{{ i18n.t().fieldEmail }}</label>
              <input pInputText formControlName="email" autocomplete="email" />

              <label>{{ i18n.t().fieldPassword }}</label>
              <p-password
                formControlName="password"
                [toggleMask]="true"
                [feedback]="true"
                [inputStyle]="{ width: '100%' }"
                [style]="{ width: '100%' }"
              />

              <label>{{ i18n.t().fieldMonthlyBudgetOptional }}</label>
              <input pInputText formControlName="monthlyBudget" placeholder="2500.00" />

              <div class="consent-row">
                <p-checkbox formControlName="gdprConsent" [binary]="true" inputId="gdprConsent" />
                <label for="gdprConsent" class="consent-label">
                  {{ i18n.t().gdprConsentPrefix }}
                  <a routerLink="/privacy" target="_blank" rel="noopener">{{ i18n.t().privacyPolicy }}</a>
                  {{ i18n.t().gdprConsentAnd }}
                  <a routerLink="/terms" target="_blank" rel="noopener">{{ i18n.t().termsOfService }}</a>{{ i18n.t().gdprConsentSuffix }}
                </label>
              </div>
              @if (registerForm.get('gdprConsent')?.invalid && registerForm.get('gdprConsent')?.touched) {
                <small class="consent-error">{{ i18n.t().gdprConsentError }}</small>
              }

              <button pButton type="submit" [label]="i18n.t().btnCreateAccount" [loading]="registerLoading()"></button>
            </form>
          </p-tabPanel>
        </p-tabView>
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
        width: min(560px, 100%);
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
      .auth-form :is(input, .p-password) {
        width: 100%;
      }
      .auth-form button {
        margin-top: 10px;
      }
      .consent-row {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        margin-top: 10px;
      }
      .consent-label {
        font-size: 0.875rem;
        color: #475569;
        line-height: 1.4;
      }
      .consent-label a {
        color: var(--primary-color, #6366f1);
        text-decoration: underline;
      }
      .consent-error {
        color: #ef4444;
        font-size: 0.8rem;
      }
      .forgot-link {
        display: inline-block;
        margin-top: 6px;
        color: #0c4a6e;
        text-decoration: none;
        font-size: 13px;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuthPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  activeTab = 0;
  readonly loginLoading = signal(false);
  readonly registerLoading = signal(false);

  readonly loginForm = this.fb.nonNullable.group({
    email: ["", [Validators.required, Validators.email]],
    password: ["", [Validators.required, Validators.minLength(10)]]
  });

  readonly registerForm = this.fb.nonNullable.group({
    companyName: ["", [Validators.required, Validators.minLength(2)]],
    companyDomain: [""],
    fullName: ["", [Validators.required, Validators.minLength(2)]],
    email: ["", [Validators.required, Validators.email]],
    password: ["", [Validators.required, Validators.minLength(10)]],
    monthlyBudget: [""],
    gdprConsent: [false, [Validators.requiredTrue]]
  });

  login(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.loginLoading.set(true);
    this.api
      .login(this.loginForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.session.setToken(response.accessToken);
          void this.router.navigateByUrl(this.redirectUrl());
          this.loginLoading.set(false);
        },
        error: () => this.loginLoading.set(false)
      });
  }

  register(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.registerLoading.set(true);
    const form = this.registerForm.getRawValue();
    this.api
      .register({
        companyName: form.companyName.trim(),
        companyDomain: form.companyDomain.trim(),
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        monthlyBudget: form.monthlyBudget.trim() === "" ? null : form.monthlyBudget.trim(),
        gdprConsent: form.gdprConsent
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.session.setToken(response.accessToken);
          void this.router.navigateByUrl(this.redirectUrl());
          this.registerLoading.set(false);
        },
        error: () => this.registerLoading.set(false)
      });
  }

  private redirectUrl(): string {
    const candidate = this.route.snapshot.queryParamMap.get("redirect");
    if (!candidate || !candidate.startsWith("/")) {
      return "/dashboard";
    }
    return candidate;
  }
}
