import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CardModule } from "primeng/card";
import { InputTextModule } from "primeng/inputtext";
import { PasswordModule } from "primeng/password";
import { ButtonModule } from "primeng/button";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { AuthSessionService } from "../../core/services/auth-session.service";
import { I18nService } from "../../core/services/i18n.service";

@Component({
  selector: "app-accept-invite-page",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardModule, InputTextModule, PasswordModule, ButtonModule],
  template: `
    <section class="auth-shell fade-in">
      <p-card styleClass="auth-card">
        <h1 class="auth-title">{{ i18n.t().acceptInviteTitle }}</h1>
        <p class="auth-subtitle">{{ i18n.t().acceptInviteSubtitle }}</p>

        <form [formGroup]="form" (ngSubmit)="acceptInvite()" class="auth-form">
          <label>{{ i18n.t().fieldInvitationToken }}</label>
          <input pInputText formControlName="token" />

          <label>{{ i18n.t().fieldFullName }}</label>
          <input pInputText formControlName="fullName" autocomplete="name" />

          <label>{{ i18n.t().fieldPassword }}</label>
          <p-password
            formControlName="password"
            [toggleMask]="true"
            [feedback]="true"
            [inputStyle]="{ width: '100%' }"
            [style]="{ width: '100%' }"
          />

          <button pButton type="submit" [label]="i18n.t().btnJoinWorkspace" [loading]="loading()"></button>
        </form>

        <a routerLink="/auth" class="back-link">{{ i18n.t().backToLogin }}</a>
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
      .back-link {
        display: inline-block;
        margin-top: 12px;
        color: #0c4a6e;
        text-decoration: none;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AcceptInvitePageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly session = inject(AuthSessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    token: ["", [Validators.required, Validators.minLength(32)]],
    fullName: ["", [Validators.required, Validators.minLength(2)]],
    password: ["", [Validators.required, Validators.minLength(10)]]
  });

  constructor() {
    const token = this.route.snapshot.queryParamMap.get("token");
    if (token) {
      this.form.patchValue({ token });
    }
  }

  acceptInvite(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.api
      .acceptInvite(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.session.setToken(response.accessToken);
          void this.router.navigateByUrl("/dashboard");
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      });
  }
}
