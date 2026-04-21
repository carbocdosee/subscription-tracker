import { ChangeDetectionStrategy, Component } from "@angular/core";
import { RouterLink } from "@angular/router";

@Component({
  selector: "app-privacy-page",
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="legal-shell fade-in">
      <article class="legal-card">
        <h1>Privacy Policy</h1>
        <p class="meta">Last updated: 18 March 2026</p>

        <section>
          <h2>1. Who we are</h2>
          <p>
            SaaS Subscription Tracker ("we", "our", "the service") is a B2B web application that helps companies
            manage their software subscriptions. The data controller for personal data collected through this service
            is the organisation operating this instance.
          </p>
        </section>

        <section>
          <h2>2. What data we collect and why</h2>
          <table>
            <thead>
              <tr><th>Data</th><th>Purpose</th><th>Legal basis (GDPR Art. 6)</th></tr>
            </thead>
            <tbody>
              <tr>
                <td>Name, work email address</td>
                <td>Account creation and authentication</td>
                <td>Art. 6(1)(b) — contract performance</td>
              </tr>
              <tr>
                <td>Hashed password, refresh tokens</td>
                <td>Secure authentication</td>
                <td>Art. 6(1)(b) — contract performance</td>
              </tr>
              <tr>
                <td>Subscription details entered by your team</td>
                <td>Core product functionality</td>
                <td>Art. 6(1)(b) — contract performance</td>
              </tr>
              <tr>
                <td>Audit log entries (actions you perform)</td>
                <td>Security and accountability</td>
                <td>Art. 6(1)(f) — legitimate interests</td>
              </tr>
              <tr>
                <td>Email delivery logs</td>
                <td>Troubleshooting email delivery</td>
                <td>Art. 6(1)(f) — legitimate interests</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>3. Data retention</h2>
          <ul>
            <li>Account data is retained while your account is active.</li>
            <li>Refresh tokens expire after 30 days and are automatically purged.</li>
            <li>Email delivery logs are purged after 90 days.</li>
            <li>Audit log entries are retained for 2 years, then automatically deleted.</li>
            <li>Password reset tokens expire after 1 hour.</li>
          </ul>
        </section>

        <section>
          <h2>4. Your rights</h2>
          <p>Under the GDPR you have the right to:</p>
          <ul>
            <li><strong>Access</strong> — request a copy of your personal data via <em>Settings → Account → Export my data</em>.</li>
            <li><strong>Portability</strong> — receive your data in a machine-readable JSON format.</li>
            <li><strong>Erasure</strong> — delete your account and associated personal data via <em>Settings → Account → Delete account</em>.</li>
            <li><strong>Rectification</strong> — update your name or email through the account settings.</li>
            <li><strong>Object</strong> — contact us to object to any processing based on legitimate interests.</li>
          </ul>
        </section>

        <section>
          <h2>5. Data sharing</h2>
          <p>
            We do not sell or rent personal data. Data may be shared with the following sub-processors
            only as necessary to operate the service: email delivery providers (Resend or SMTP relay),
            and cloud infrastructure providers. All sub-processors are bound by appropriate data processing
            agreements.
          </p>
        </section>

        <section>
          <h2>6. Security</h2>
          <p>
            Passwords are hashed using BCrypt. All tokens are stored as SHA-256 hashes. Communication
            between your browser and the service is encrypted via TLS. We apply the principle of
            least privilege throughout the application.
          </p>
        </section>

        <section>
          <h2>7. Contact</h2>
          <p>
            For privacy-related requests or questions, please contact the administrator of this
            SaaS Subscription Tracker instance.
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
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 14px;
        margin-top: 8px;
      }
      th {
        background: #f8fafc;
        text-align: left;
        padding: 10px 12px;
        color: #475569;
        font-weight: 600;
        border: 1px solid #e2e8f0;
      }
      td {
        padding: 10px 12px;
        border: 1px solid #e2e8f0;
        color: #334155;
        vertical-align: top;
      }
      tr:nth-child(even) td {
        background: #f8fafc;
      }
      .back-link {
        display: inline-block;
        margin-top: 32px;
        color: #0c4a6e;
        text-decoration: none;
        font-size: 14px;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PrivacyPageComponent {}
