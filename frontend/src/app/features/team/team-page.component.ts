import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { MessageService } from "primeng/api";
import { TableModule } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { InputNumberModule } from "primeng/inputnumber";
import { DropdownModule } from "primeng/dropdown";
import { CardModule } from "primeng/card";
import { TagModule } from "primeng/tag";
import { TooltipModule } from "primeng/tooltip";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { I18nService } from "../../core/services/i18n.service";
import { TeamInvitation, TeamInviteResponse, TeamMember } from "../../shared/models";

@Component({
  standalone: true,
  selector: "app-team-page",
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, CardModule, TagModule, TooltipModule],
  template: `
    <h2 class="page-title">{{ i18n.t().teamTitle }}</h2>
    <p class="section-subtitle">{{ i18n.t().teamSubtitle }}</p>

    <section class="card mb-3">
      <h3>{{ i18n.t().companySettings }}</h3>
      <div class="settings-row">
        <div class="field">
          <label>{{ i18n.t().numberOfEmployees }}</label>
          <p-inputNumber
            [(ngModel)]="employeeCount"
            [min]="1"
            [max]="100000"
            [placeholder]="i18n.t().autoCountPlaceholder"
          ></p-inputNumber>
          <small class="field-hint">{{ i18n.t().employeeCountHint }}</small>
        </div>
        <div class="field action-field">
          <button pButton type="button" [label]="i18n.t().btnSave" [loading]="savingSettings()" (click)="saveCompanySettings()"></button>
        </div>
      </div>
    </section>

    <section class="card mb-3">
      <h3>{{ i18n.t().inviteMemberTitle }}</h3>
      <form [formGroup]="inviteForm" (ngSubmit)="invite()" class="invite-row">
        <div class="field">
          <label>{{ i18n.t().fieldEmail }}</label>
          <input pInputText formControlName="email" placeholder="user@company.com" />
          <small *ngIf="emailInvalid()" class="field-error">{{ i18n.t().enterValidEmail }}</small>
        </div>
        <div class="field role-field">
          <label>{{ i18n.t().fieldRole }}</label>
          <p-dropdown [options]="roles()" formControlName="role" optionLabel="label" optionValue="value"></p-dropdown>
        </div>
        <div class="field action-field">
          <button pButton type="submit" [label]="i18n.t().btnSendInvite" [loading]="loadingInvite()"></button>
        </div>
      </form>

      <div class="tip">
        <i class="pi pi-info-circle"></i>
        <span>{{ i18n.t().teamInviteTip }}</span>
      </div>

      <div *ngIf="lastInviteLink()" class="invite-link">
        <span>{{ i18n.t().latestInviteLink }}</span>
        <a [href]="lastInviteLink()" target="_blank" rel="noopener noreferrer">{{ lastInviteLink() }}</a>
      </div>

      <div *ngIf="lastEmailDeliveryStatus()" class="delivery-state" [class.delivery-state--warn]="lastEmailDeliveryStatus() !== 'SENT'">
        {{ i18n.t().emailDelivery }}
        <strong>{{ lastEmailDeliveryStatus() }}</strong>
        <span *ngIf="lastEmailProviderCode()"> (code {{ lastEmailProviderCode() }})</span>
        <span *ngIf="lastEmailDeliveryMessage()"> - {{ lastEmailDeliveryMessage() }}</span>
      </div>
    </section>

    <section class="card mb-3">
      <div class="section-head">
        <h3>{{ i18n.t().membersTitle }}</h3>
        <span class="chip">{{ membersActiveLabel() }}</span>
      </div>
      <div *ngIf="membersError()" class="error-state">{{ membersError() }}</div>
      <p-table [value]="members()">
        <ng-template pTemplate="header">
          <tr>
            <th>{{ i18n.t().thName }}</th>
            <th>{{ i18n.t().fieldEmail }}</th>
            <th>{{ i18n.t().fieldRole }}</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-member>
          <tr>
            <td>{{ member.name }}</td>
            <td>{{ member.email }}</td>
            <td>
              <p-tag [value]="member.role" [severity]="roleSeverity(member.role)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="3">{{ loadingMembers() ? i18n.t().loadingMembersText : i18n.t().noActiveMembersText }}</td>
          </tr>
        </ng-template>
      </p-table>
    </section>

    <section class="card">
      <div class="section-head">
        <h3>{{ i18n.t().activeInvitationsTitle }}</h3>
        <span class="chip">{{ invitationsPendingLabel() }}</span>
      </div>
      <div *ngIf="invitationsError()" class="error-state">{{ invitationsError() }}</div>
      <p-table [value]="activeInvitations()">
        <ng-template pTemplate="header">
          <tr>
            <th>{{ i18n.t().fieldEmail }}</th>
            <th>{{ i18n.t().fieldRole }}</th>
            <th>{{ i18n.t().thCreated }}</th>
            <th>{{ i18n.t().thExpires }}</th>
            <th>{{ i18n.t().thStatus }}</th>
            <th class="actions-col-header">{{ i18n.t().thActions }}</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-invitation>
          <tr>
            <td>{{ invitation.email }}</td>
            <td><p-tag [value]="invitation.role" [severity]="roleSeverity(invitation.role)" /></td>
            <td>{{ invitation.createdAt | date: "yyyy-MM-dd HH:mm" }}</td>
            <td>{{ invitation.expiresAt | date: "yyyy-MM-dd HH:mm" }}</td>
            <td><p-tag [value]="invitationStatusLabel(invitation)" [severity]="invitationStatusSeverity(invitation)" /></td>
            <td class="actions-col">
              <div class="actions-row">
                <button
                  *ngIf="invitation.acceptInviteUrl"
                  pButton
                  type="button"
                  class="p-button-sm p-button-text"
                  icon="pi pi-copy"
                  (click)="copyInviteLink(invitation)"
                  [pTooltip]="i18n.t().tooltipCopyInviteLink"
                  tooltipPosition="top"
                ></button>
                <button
                  pButton
                  type="button"
                  class="p-button-sm p-button-text"
                  icon="pi pi-refresh"
                  [loading]="resendingEmail() === invitation.email"
                  (click)="resendInvite(invitation)"
                  [pTooltip]="i18n.t().tooltipResendInvitation"
                  tooltipPosition="top"
                ></button>
                <button
                  pButton
                  type="button"
                  class="p-button-sm p-button-text p-button-danger"
                  icon="pi pi-times"
                  [loading]="cancelingInvitation() === invitation.id"
                  (click)="cancelInvite(invitation)"
                  [pTooltip]="i18n.t().tooltipCancelInvitation"
                  tooltipPosition="top"
                ></button>
              </div>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="6">{{ loadingInvitations() ? i18n.t().loadingInvitationsText : i18n.t().noActiveInvitationsText }}</td>
          </tr>
        </ng-template>
      </p-table>
    </section>
  `,
  styles: [
    `
      .settings-row {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 10px;
        align-items: end;
      }
      .field-hint {
        color: #64748b;
        font-size: 0.82rem;
      }
      .invite-row {
        display: grid;
        grid-template-columns: 2fr 1fr auto;
        gap: 10px;
        align-items: end;
      }
      .field {
        display: grid;
        gap: 6px;
      }
      .field label {
        color: #475569;
        font-size: 0.9rem;
      }
      .field :is(input, .p-dropdown) {
        width: 100%;
      }
      .field-error {
        color: var(--danger);
        font-size: 0.82rem;
      }
      .action-field {
        padding-bottom: 2px;
      }
      .tip {
        margin-top: 12px;
        border: 1px solid #dbeafe;
        background: #eff6ff;
        color: #1e3a8a;
        border-radius: 10px;
        padding: 9px 12px;
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .invite-link {
        margin-top: 10px;
        font-size: 0.9rem;
        display: grid;
        gap: 4px;
      }
      .invite-link a {
        color: #0b61d8;
        text-decoration: none;
      }
      .delivery-state {
        margin-top: 8px;
        color: #14532d;
        font-size: 0.88rem;
      }
      .delivery-state--warn {
        color: #9a3412;
      }
      .section-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        margin-bottom: 12px;
      }
      .section-head h3 {
        margin: 0;
      }
      .chip {
        border: 1px solid #c8d9ef;
        background: #f1f6fd;
        border-radius: 999px;
        padding: 5px 10px;
        color: #334155;
        font-size: 0.83rem;
      }
      .error-state {
        margin-bottom: 10px;
        color: var(--danger);
      }
      .actions-col-header {
        width: 140px;
        text-align: right;
      }
      .actions-col {
        text-align: right;
        width: 140px;
      }
      .actions-row {
        width: 100%;
        display: inline-flex;
        justify-content: flex-end;
        align-items: center;
        gap: 4px;
      }
      @media (max-width: 860px) {
        .invite-row {
          grid-template-columns: 1fr;
        }
        .action-field button {
          width: 100%;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TeamPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly api = inject(TrackerApiService);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  employeeCount: number | null = null;
  readonly savingSettings = signal(false);

  readonly members = signal<TeamMember[]>([]);
  readonly activeInvitations = signal<TeamInvitation[]>([]);
  readonly loadingMembers = signal(false);
  readonly loadingInvitations = signal(false);
  readonly loadingInvite = signal(false);
  readonly resendingEmail = signal<string | null>(null);
  readonly cancelingInvitation = signal<string | null>(null);
  readonly lastInviteLink = signal<string | null>(null);
  readonly lastEmailDeliveryStatus = signal<"SENT" | "SKIPPED_NOT_CONFIGURED" | "FAILED" | null>(null);
  readonly lastEmailDeliveryMessage = signal<string | null>(null);
  readonly lastEmailProviderCode = signal<number | null>(null);
  readonly membersError = signal<string | null>(null);
  readonly invitationsError = signal<string | null>(null);

  readonly roles = computed(() => {
    const t = this.i18n.t();
    return [
      { label: t.roleViewer, value: "VIEWER" },
      { label: t.roleEditor, value: "EDITOR" },
      { label: t.roleAdmin, value: "ADMIN" }
    ];
  });

  readonly inviteForm = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
    role: ["VIEWER", [Validators.required]]
  });

  constructor() {
    this.reloadTeamData();
    this.loadCompanySettings();
  }

  membersActiveLabel(): string {
    const count = this.members().length;
    if (this.i18n.lang() === "ru") {
      return this.i18n.pluralRu(count, "активный", "активных", "активных");
    }
    return `${count} active`;
  }

  invitationsPendingLabel(): string {
    const count = this.activeInvitations().length;
    if (this.i18n.lang() === "ru") {
      return this.i18n.pluralRu(count, "ожидающее", "ожидающих", "ожидающих");
    }
    return `${count} pending`;
  }

  saveCompanySettings(): void {
    this.savingSettings.set(true);
    this.api
      .updateCompany({ employeeCount: this.employeeCount })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.employeeCount = result.employeeCount;
          this.savingSettings.set(false);
          const t = this.i18n.t();
          this.messageService.add({ severity: "success", summary: t.msgSettingsSaved, detail: t.msgSettingsSavedDetail });
        },
        error: () => this.savingSettings.set(false)
      });
  }

  invite(): void {
    if (this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }

    const payload = this.inviteForm.getRawValue();
    this.loadingInvite.set(true);
    this.api
      .inviteMember({ email: payload.email ?? "", role: (payload.role as "ADMIN" | "EDITOR" | "VIEWER") ?? "VIEWER" })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.lastInviteLink.set(result.acceptInviteUrl);
          this.lastEmailDeliveryStatus.set(result.emailDelivery.status);
          this.lastEmailDeliveryMessage.set(result.emailDelivery.message ?? null);
          this.lastEmailProviderCode.set(result.emailDelivery.providerStatusCode ?? null);
          this.inviteForm.reset({ role: "VIEWER" });
          this.reloadTeamData();
          const t = this.i18n.t();
          this.notifyInviteResult(result, result.reusedExisting ? t.msgInvitationResent : t.msgInvitationCreated);
          this.loadingInvite.set(false);
        },
        error: () => {
          this.loadingInvite.set(false);
        }
      });
  }

  resendInvite(invitation: TeamInvitation): void {
    this.resendingEmail.set(invitation.email);
    this.api
      .resendInvitation(invitation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.lastInviteLink.set(result.acceptInviteUrl);
          this.lastEmailDeliveryStatus.set(result.emailDelivery.status);
          this.lastEmailDeliveryMessage.set(result.emailDelivery.message ?? null);
          this.lastEmailProviderCode.set(result.emailDelivery.providerStatusCode ?? null);
          this.reloadInvitations();
          this.notifyInviteResult(result, this.i18n.t().msgInvitationResent);
          this.resendingEmail.set(null);
        },
        error: () => this.resendingEmail.set(null)
      });
  }

  cancelInvite(invitation: TeamInvitation): void {
    const t = this.i18n.t();
    const confirmed = window.confirm(t.confirmCancelInvitationFor.replace("{0}", invitation.email));
    if (!confirmed) return;

    this.cancelingInvitation.set(invitation.id);
    this.api
      .cancelInvitation(invitation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.reloadInvitations();
          this.cancelingInvitation.set(null);
          this.messageService.add({
            severity: "success",
            summary: t.msgInvitationCanceled,
            detail: t.msgInvitationCanceledDetail.replace("{0}", invitation.email)
          });
        },
        error: () => this.cancelingInvitation.set(null)
      });
  }

  copyInviteLink(invitation: TeamInvitation): void {
    const t = this.i18n.t();
    const link = invitation.acceptInviteUrl ?? this.lastInviteLink();
    if (!link) {
      this.messageService.add({
        severity: "warn",
        summary: t.msgLinkUnavailable,
        detail: t.msgLinkUnavailableDetail
      });
      return;
    }

    navigator.clipboard
      .writeText(link)
      .then(() => {
        this.messageService.add({
          severity: "success",
          summary: t.msgCopySuccess,
          detail: t.msgCopySuccessDetail
        });
      })
      .catch(() => {
        this.messageService.add({
          severity: "warn",
          summary: t.msgCopyFailed,
          detail: t.msgCopyFailedDetail
        });
      });
  }

  roleSeverity(role: TeamMember["role"]): "danger" | "warning" | "info" {
    if (role === "ADMIN") return "danger";
    if (role === "EDITOR") return "warning";
    return "info";
  }

  invitationStatusLabel(invitation: TeamInvitation): string {
    const hoursLeft = this.hoursUntil(invitation.expiresAt);
    if (hoursLeft <= 24) return this.i18n.t().invitationExpiringSoon;
    return this.i18n.t().invitationPending;
  }

  invitationStatusSeverity(invitation: TeamInvitation): "danger" | "warning" {
    const hoursLeft = this.hoursUntil(invitation.expiresAt);
    return hoursLeft <= 24 ? "danger" : "warning";
  }

  emailInvalid(): boolean {
    const control = this.inviteForm.controls.email;
    return control.invalid && (control.touched || control.dirty);
  }

  private notifyInviteResult(result: TeamInviteResponse, summary: string): void {
    const t = this.i18n.t();
    if (result.emailDelivery.status === "SENT") {
      this.messageService.add({
        severity: "success",
        summary,
        detail: t.msgInviteEmailSentTo.replace("{0}", result.invitation.email)
      });
      return;
    }

    if (result.emailDelivery.status === "SKIPPED_NOT_CONFIGURED") {
      this.messageService.add({
        severity: "warn",
        summary: `${summary} (${t.msgInvitationResentEmailSkipped})`,
        detail: t.msgEmailProviderNotConfigured
      });
      return;
    }

    this.messageService.add({
      severity: "warn",
      summary: `${summary} (${t.msgInvitationEmailFailed})`,
      detail: result.emailDelivery.message ?? t.msgEmailDeliveryFailed
    });
  }

  private reloadTeamData(): void {
    this.reloadMembers();
    this.reloadInvitations();
  }

  private reloadMembers(): void {
    this.loadingMembers.set(true);
    this.membersError.set(null);
    this.api
      .getMembers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.members.set(result.items);
          this.loadingMembers.set(false);
        },
        error: () => {
          this.membersError.set(this.i18n.t().loadingMembersText);
          this.loadingMembers.set(false);
        }
      });
  }

  private reloadInvitations(): void {
    this.loadingInvitations.set(true);
    this.invitationsError.set(null);
    this.api
      .getActiveInvitations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.activeInvitations.set(result.items);
          this.loadingInvitations.set(false);
        },
        error: () => {
          this.invitationsError.set(this.i18n.t().noActiveInvitationsText);
          this.loadingInvitations.set(false);
        }
      });
  }

  private loadCompanySettings(): void {
    this.api
      .getCompanySettings()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.employeeCount = result.employeeCount;
        },
        error: () => {}
      });
  }

  private hoursUntil(date: string): number {
    const expiresAt = new Date(date).getTime();
    const now = Date.now();
    return Math.max(Math.floor((expiresAt - now) / (1000 * 60 * 60)), 0);
  }
}
