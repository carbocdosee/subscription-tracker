import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import {
  AnalyticsData,
  AuthResponse,
  CategoriesResponse,
  CompanySettings,
  DashboardStats,
  NotificationFeedResponse,
  OnboardingSettings,
  PagedSubscriptionListResponse,
  SubscriptionItem,
  TeamInvitation,
  TeamInviteResponse,
  TeamMember
} from "../../shared/models";

const API_BASE = "/api/v1";
const AUTH_BASE = "/api/auth";

@Injectable({ providedIn: "root" })
export class TrackerApiService {
  constructor(private readonly http: HttpClient) {}

  login(payload: { email: string; password: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${AUTH_BASE}/login`, payload);
  }

  refreshToken(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${AUTH_BASE}/refresh`, {}, { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${AUTH_BASE}/logout`, {}, { withCredentials: true });
  }

  acceptInvite(payload: { token: string; fullName: string; password: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${AUTH_BASE}/accept-invite`, payload);
  }

  register(payload: {
    companyName: string;
    companyDomain: string;
    fullName: string;
    email: string;
    password: string;
    monthlyBudget?: string | null;
  }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${AUTH_BASE}/register`, payload);
  }

  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${API_BASE}/dashboard/stats`);
  }

  getNotifications(limit = 20): Observable<NotificationFeedResponse> {
    return this.http.get<NotificationFeedResponse>(`${API_BASE}/notifications?limit=${Math.max(1, Math.min(limit, 100))}`);
  }

  getNotificationsUnreadCount(): Observable<{ unreadCount: number }> {
    return this.http.get<{ unreadCount: number }>(`${API_BASE}/notifications/unread-count`);
  }

  markNotificationsRead(keys: string[]): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${API_BASE}/notifications/read`, { keys });
  }

  markAllNotificationsRead(): Observable<{ ok: boolean; marked: number }> {
    return this.http.post<{ ok: boolean; marked: number }>(`${API_BASE}/notifications/read-all`, {});
  }

  getSubscriptions(params?: {
    page?: number;
    size?: number;
    vendor?: string;
    category?: string;
    status?: string;
    sortBy?: string;
    sortDir?: string;
  }): Observable<PagedSubscriptionListResponse> {
    let httpParams = new HttpParams();
    if (params?.page != null) httpParams = httpParams.set("page", String(params.page));
    if (params?.size != null) httpParams = httpParams.set("size", String(params.size));
    if (params?.vendor) httpParams = httpParams.set("vendor", params.vendor);
    if (params?.category) httpParams = httpParams.set("category", params.category);
    if (params?.status) httpParams = httpParams.set("status", params.status);
    if (params?.sortBy) httpParams = httpParams.set("sortBy", params.sortBy);
    if (params?.sortDir) httpParams = httpParams.set("sortDir", params.sortDir);
    return this.http.get<PagedSubscriptionListResponse>(`${API_BASE}/subscriptions`, { params: httpParams });
  }

  createSubscription(payload: Record<string, unknown>): Observable<SubscriptionItem> {
    return this.http.post<SubscriptionItem>(`${API_BASE}/subscriptions`, payload);
  }

  updateSubscription(id: string, payload: Record<string, unknown>): Observable<SubscriptionItem> {
    return this.http.put<SubscriptionItem>(`${API_BASE}/subscriptions/${id}`, payload);
  }

  deleteSubscription(id: string): Observable<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(`${API_BASE}/subscriptions/${id}`);
  }

  markSubscriptionPaid(
    id: string,
    payload: { paidAt?: string; amount?: string; paymentReference?: string; note?: string } = {}
  ): Observable<SubscriptionItem> {
    return this.http.post<SubscriptionItem>(`${API_BASE}/subscriptions/${id}/mark-paid`, payload);
  }

  downloadExport(format: 'csv' | 'pdf', params?: { category?: string; status?: string }): Observable<Blob> {
    let httpParams = new HttpParams().set('format', format);
    if (params?.category) httpParams = httpParams.set('category', params.category);
    if (params?.status) httpParams = httpParams.set('status', params.status);
    return this.http.get(`${API_BASE}/subscriptions/export`, { params: httpParams, responseType: 'blob' });
  }

  getCategories(): Observable<CategoriesResponse> {
    return this.http.get<CategoriesResponse>(`${API_BASE}/subscriptions/categories`);
  }

  importCsv(file: File): Observable<{ imported: number; skipped: number; errors: string[] }> {
    const data = new FormData();
    data.append("file", file);
    return this.http.post<{ imported: number; skipped: number; errors: string[] }>(
      `${API_BASE}/subscriptions/import/csv`,
      data
    );
  }

  createCheckout(plan: "monthly" | "annual" = "monthly"): Observable<{ checkoutUrl: string }> {
    return this.http.post<{ checkoutUrl: string }>(`${API_BASE}/billing/checkout?plan=${plan}`, {});
  }

  getBillingPortal(): Observable<{ portalUrl: string }> {
    return this.http.get<{ portalUrl: string }>(`${API_BASE}/billing/portal`);
  }

  getBillingStatus(): Observable<{ subscriptionStatus: string }> {
    return this.http.get<{ subscriptionStatus: string }>(`${API_BASE}/billing/status`);
  }

  getAnalytics(): Observable<AnalyticsData> {
    return this.http.get<AnalyticsData>(`${API_BASE}/analytics`);
  }

  getCompanySettings(): Observable<CompanySettings> {
    return this.http.get<CompanySettings>(`${API_BASE}/company`);
  }

  updateCompany(payload: {
    monthlyBudget?: string | null;
    employeeCount?: number | null;
    onboarding?: Partial<OnboardingSettings> | null;
  }): Observable<CompanySettings> {
    return this.http.patch<CompanySettings>(`${API_BASE}/company`, payload);
  }

  getMembers(): Observable<{ items: TeamMember[] }> {
    return this.http.get<{ items: TeamMember[] }>(`${API_BASE}/team/members`);
  }

  getActiveInvitations(): Observable<{ items: TeamInvitation[] }> {
    return this.http.get<{ items: TeamInvitation[] }>(`${API_BASE}/team/invitations`);
  }

  inviteMember(payload: { email: string; role: "ADMIN" | "EDITOR" | "VIEWER" }): Observable<TeamInviteResponse> {
    return this.http.post<TeamInviteResponse>(`${API_BASE}/team/invite`, payload);
  }

  resendInvitation(invitationId: string): Observable<TeamInviteResponse> {
    return this.http.post<TeamInviteResponse>(`${API_BASE}/team/invitations/${invitationId}/resend`, {});
  }

  cancelInvitation(invitationId: string): Observable<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(`${API_BASE}/team/invitations/${invitationId}`);
  }

  getInvitationDeliveryHistory(invitationId: string): Observable<{
    items: Array<{
      id: string;
      invitationId?: string | null;
      recipientEmail: string;
      status: "SENT" | "SKIPPED_NOT_CONFIGURED" | "FAILED";
      providerMessageId?: string | null;
      providerStatusCode?: number | null;
      message?: string | null;
      createdAt: string;
    }>;
  }> {
    return this.http.get<{
      items: Array<{
        id: string;
        invitationId?: string | null;
        recipientEmail: string;
        status: "SENT" | "SKIPPED_NOT_CONFIGURED" | "FAILED";
        providerMessageId?: string | null;
        providerStatusCode?: number | null;
        message?: string | null;
        createdAt: string;
      }>;
    }>(`${API_BASE}/team/invitations/${invitationId}/delivery-history`);
  }
}
