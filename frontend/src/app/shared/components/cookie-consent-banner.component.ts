import { ChangeDetectionStrategy, Component, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { ButtonModule } from "primeng/button";

const CONSENT_KEY = "cookie_consent_v1";

@Component({
  selector: "app-cookie-consent-banner",
  standalone: true,
  imports: [RouterLink, ButtonModule],
  template: `
    @if (visible()) {
      <div class="cookie-banner" role="dialog" aria-label="Cookie consent">
        <p class="cookie-text">
          This site uses only strictly necessary cookies required for authentication and security.
          No tracking or advertising cookies are used.
          See our <a routerLink="/privacy">Privacy Policy</a> for details.
        </p>
        <div class="cookie-actions">
          <button pButton label="Got it" (click)="accept()"></button>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .cookie-banner {
        position: fixed;
        bottom: 0;
        left: 0;
        right: 0;
        background: #1e293b;
        color: #f1f5f9;
        padding: 14px 24px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        z-index: 9999;
        box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.2);
      }
      .cookie-text {
        margin: 0;
        font-size: 14px;
        color: #cbd5e1;
        flex: 1;
      }
      .cookie-text a {
        color: #7dd3fc;
        text-decoration: underline;
      }
      .cookie-actions {
        flex-shrink: 0;
      }
      @media (max-width: 600px) {
        .cookie-banner {
          flex-direction: column;
          align-items: flex-start;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CookieConsentBannerComponent {
  readonly visible = signal(!localStorage.getItem(CONSENT_KEY));

  accept(): void {
    localStorage.setItem(CONSENT_KEY, "accepted");
    this.visible.set(false);
  }
}
