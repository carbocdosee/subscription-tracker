export interface SubscriptionItem {
  id: string;
  vendorName: string;
  vendorUrl?: string;
  vendorLogoUrl?: string;
  category: string;
  amount: string;
  currency: string;
  billingCycle: "MONTHLY" | "QUARTERLY" | "ANNUAL";
  renewalDate: string;
  paymentMode: "AUTO" | "MANUAL";
  paymentStatus: "PAID" | "PENDING" | "OVERDUE";
  lastPaidAt?: string | null;
  nextPaymentDate?: string | null;
  status: "ACTIVE" | "PAUSED" | "CANCELED" | "EXPIRED";
  healthScore: "GOOD" | "WARNING" | "CRITICAL";
  duplicateWarnings: string[];
}

export interface PagedSubscriptionListResponse {
  items: SubscriptionItem[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface AuthUserSummary {
  id: string;
  companyId: string;
  email: string;
  name: string;
  role: "ADMIN" | "EDITOR" | "VIEWER";
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: AuthUserSummary;
}

export interface DashboardStats {
  totalMonthlySpend: string;
  totalAnnualSpend: string;
  subscriptionCount: number;
  totalRenewal30DaysAmount: string;
  renewingIn30Days: Array<{
    subscriptionId: string;
    vendorName: string;
    amountUsd: string;
    renewalDate: string;
    daysLeft: number;
    alertType: string;
  }>;
  spendByCategory: Record<string, string>;
  monthlyTrend: Array<{ month: string; amountUsd: string }>;
  topCostDrivers: Array<{
    vendorName: string;
    monthlySpendUsd: string;
    subscriptionCount: number;
  }>;
  duplicateWarnings: Array<{
    type: string;
    key: string;
    subscriptionIds: string[];
    estimatedSavingsUsd: string;
  }>;
  potentialSavings: string;
}

export interface AnalyticsData {
  yoySpendComparison: {
    dataAvailable: boolean;
    currentYear?: number;
    previousYear?: number;
    currentYearUsd?: string;
    previousYearUsd?: string;
    growthPercent?: string;
  };
  fastestGrowingSubscriptions: Array<{
    subscriptionId: string;
    vendorName: string;
    previousMonthlyUsd: string;
    currentMonthlyUsd: string;
    growthPercent: string;
  }>;
  budgetGauge?: {
    budgetUsd: string;
    actualUsd: string;
    utilizationPercent: string;
    overBudget: boolean;
  };
  costPerEmployee: {
    employeeCount: number;
    monthlyUsd: string;
    annualUsd: string;
  };
  healthScore: "GOOD" | "WARNING" | "CRITICAL";
}

export interface TeamMember {
  id: string;
  name: string;
  email: string;
  role: "ADMIN" | "EDITOR" | "VIEWER";
  isActive?: boolean;
}

export interface TeamInvitation {
  id: string;
  email: string;
  role: "ADMIN" | "EDITOR" | "VIEWER";
  expiresAt: string;
  acceptedAt?: string | null;
  createdAt: string;
  acceptInviteUrl?: string | null;
}

export interface TeamInviteResponse {
  invitation: TeamInvitation;
  reusedExisting: boolean;
  acceptInviteUrl: string;
  emailDelivery: {
    status: "SENT" | "SKIPPED_NOT_CONFIGURED" | "FAILED";
    message?: string | null;
    providerMessageId?: string | null;
    providerStatusCode?: number | null;
  };
}

export type NotificationType =
  | "RENEWAL_DUE"
  | "RENEWAL_ALERT_FAILED"
  | "MANUAL_PAYMENT_DUE"
  | "MANUAL_PAYMENT_OVERDUE"
  | "INVITATION_EXPIRING"
  | "INVITATION_EMAIL_ISSUE";

export type NotificationSeverity = "INFO" | "WARNING" | "DANGER";

export interface NotificationItem {
  key: string;
  type: NotificationType;
  severity: NotificationSeverity;
  title: string;
  message: string;
  createdAt: string;
  actionPath?: string | null;
  actionLabel?: string | null;
  read: boolean;
}

export interface NotificationFeedResponse {
  items: NotificationItem[];
  unreadCount: number;
}

export interface OnboardingSettings {
  completed: boolean;
  completedSteps: string[];
  startedAt?: string | null;
}

export interface CompanySettings {
  monthlyBudget: string | null;
  employeeCount: number | null;
  onboarding?: OnboardingSettings | null;
}

export interface CategoriesResponse {
  predefined: string[];
  custom: string[];
}
