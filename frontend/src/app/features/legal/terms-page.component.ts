import { ChangeDetectionStrategy, Component } from "@angular/core";
import { RouterLink } from "@angular/router";

@Component({
  selector: "app-terms-page",
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="legal-shell fade-in">
      <article class="legal-card">
        <h1>Terms of Service</h1>
        <p class="meta">Last updated: 18 March 2026</p>

        <section>
          <h2>1. Acceptance</h2>
          <p>
            By creating an account or using SaaS Subscription Tracker, you agree to these Terms of Service.
            If you do not agree, do not use the service.
          </p>
        </section>

        <section>
          <h2>2. Description of service</h2>
          <p>
            SaaS Subscription Tracker is a web application that enables teams to track, manage, and analyse
            their software subscription spending. The service is provided on a subscription basis (trial, then
            paid plan).
          </p>
        </section>

        <section>
          <h2>3. Accounts and security</h2>
          <ul>
            <li>You are responsible for maintaining the confidentiality of your account credentials.</li>
            <li>You must notify us immediately of any unauthorised use of your account.</li>
            <li>Each account is for a single user; credential sharing is not permitted.</li>
            <li>You must be at least 18 years old and authorised to act on behalf of your organisation.</li>
          </ul>
        </section>

        <section>
          <h2>4. Acceptable use</h2>
          <p>You agree not to:</p>
          <ul>
            <li>Use the service for any unlawful purpose or in violation of any applicable law.</li>
            <li>Attempt to gain unauthorised access to other accounts or system components.</li>
            <li>Reverse-engineer, decompile, or disassemble any part of the service.</li>
            <li>Transmit malware or interfere with the integrity or performance of the service.</li>
          </ul>
        </section>

        <section>
          <h2>5. Data ownership</h2>
          <p>
            You retain ownership of all data you input into the service. By using the service you grant us
            a limited licence to store and process that data solely to provide the service to you.
          </p>
        </section>

        <section>
          <h2>6. Trial and billing</h2>
          <ul>
            <li>New accounts begin with a 14-day free trial. No credit card is required during the trial.</li>
            <li>After the trial, continued access requires an active paid subscription.</li>
            <li>Billing is handled by Stripe. We do not store full payment card details.</li>
            <li>Subscriptions renew automatically unless cancelled before the renewal date.</li>
          </ul>
        </section>

        <section>
          <h2>7. Termination</h2>
          <p>
            You may cancel your account at any time from the account settings. We reserve the right to
            suspend or terminate accounts that violate these Terms. Upon termination, your data will be
            deleted in accordance with our <a routerLink="/privacy">Privacy Policy</a>.
          </p>
        </section>

        <section>
          <h2>8. Disclaimer and limitation of liability</h2>
          <p>
            The service is provided "as is" without warranty of any kind. To the maximum extent permitted
            by law, we are not liable for indirect, incidental, or consequential damages arising from your
            use of the service.
          </p>
        </section>

        <section>
          <h2>9. Changes to these terms</h2>
          <p>
            We may update these Terms from time to time. Continued use of the service after changes are
            posted constitutes acceptance of the updated Terms.
          </p>
        </section>

        <section>
          <h2>10. Contact</h2>
          <p>
            For any questions about these Terms, please contact the administrator of this SaaS Subscription
            Tracker instance.
          </p>
        </section>

        <a routerLink="/auth" class="back-link">← Back</a>
      </article>
    </main>
  `,
  styles: [
    `
      .legal-shell {
        min-height: 100vh;
        padding: 32px 16px;
        background: #f8fafc;
      }
      .legal-card {
        max-width: 760px;
        margin: 0 auto;
        background: #fff;
        border-radius: 16px;
        padding: 32px 40px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
      }
      h1 {
        margin: 0 0 4px 0;
        font-size: 28px;
        color: #0f172a;
      }
      .meta {
        color: #64748b;
        font-size: 13px;
        margin: 0 0 28px 0;
      }
      h2 {
        font-size: 17px;
        color: #0f172a;
        margin: 28px 0 10px 0;
      }
      p, li {
        color: #334155;
        line-height: 1.65;
        font-size: 15px;
      }
      ul {
        padding-left: 20px;
        margin: 8px 0;
      }
      li {
        margin-bottom: 6px;
      }
      a {
        color: #0c4a6e;
      }
      .back-link {
        display: inline-block;
        margin-top: 32px;
        font-size: 14px;
        text-decoration: none;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TermsPageComponent {}
