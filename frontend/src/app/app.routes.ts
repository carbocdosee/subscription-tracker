import { Routes } from "@angular/router";
import { authGuard, guestGuard } from "./core/guards/auth.guard";
import { AuthPageComponent } from "./features/auth/auth-page.component";
import { AcceptInvitePageComponent } from "./features/auth/accept-invite-page.component";
import { DashboardPageComponent } from "./features/dashboard/dashboard-page.component";
import { SubscriptionsPageComponent } from "./features/subscriptions/subscriptions-page.component";
import { AnalyticsPageComponent } from "./features/analytics/analytics-page.component";
import { TeamPageComponent } from "./features/team/team-page.component";
import { UpgradePageComponent } from "./features/billing/upgrade-page.component";
import { AccountPageComponent } from "./features/settings/account-page.component";

export const appRoutes: Routes = [
  { path: "", pathMatch: "full", redirectTo: "dashboard" },
  { path: "auth", component: AuthPageComponent, canActivate: [guestGuard] },
  { path: "accept-invite", component: AcceptInvitePageComponent, canActivate: [guestGuard] },
  { path: "dashboard", component: DashboardPageComponent, canActivate: [authGuard] },
  { path: "subscriptions", component: SubscriptionsPageComponent, canActivate: [authGuard] },
  { path: "analytics", component: AnalyticsPageComponent, canActivate: [authGuard] },
  { path: "team", component: TeamPageComponent, canActivate: [authGuard] },
  { path: "billing/upgrade", component: UpgradePageComponent, canActivate: [authGuard] },
  { path: "settings/account", component: AccountPageComponent, canActivate: [authGuard] },
  { path: "**", redirectTo: "dashboard" }
];
