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
  lastUsedAt?: string | null;
  isZombie?: boolean;
  tags?: string[];
  description?: string | null;
  notes?: string | null;
  ownerId?: string | null;
  documentUrl?: string | null;
}

export interface VendorSuggestion {
  vendorName: string;
  subscriptionId: string;
  category: string;
  similarity: number;
}

export interface VendorSuggestResponse {
  items: VendorSuggestion[];
}

export type PlanTier = "FREE" | "PRO" | "ENTERPRISE";

export interface BillingPlan {
  id: PlanTier;
  name: string;
  priceMonthly: number;
  currency: string;
  priceId: string | null;
  limits: { subscriptions: number; teamMembers: number };
  isCurrent: boolean;
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
  zombieThresholdDays?: number;
  onboarding?: OnboardingSettings | null;
}

export interface CategoriesResponse {
  predefined: string[];
  custom: string[];
}

export interface SaasTemplate {
  id: string;
  name: string;
  category: string;
  defaultBillingCycle: "MONTHLY" | "QUARTERLY" | "ANNUAL";
  defaultAmountUsd: number;
  logoUrl: string;
  websiteUrl: string;
}

export interface SaasTemplatesResponse {
  templates: SaasTemplate[];
}

export interface BatchSubscriptionItem {
  templateId?: string;
  vendorName: string;
  category: string;
  amount: string;
  currency: string;
  billingCycle: "MONTHLY" | "QUARTERLY" | "ANNUAL";
  renewalDate: string;
  vendorUrl?: string;
  logoUrl?: string;
}

export interface BatchCreateResponse {
  created: number;
  skipped: number;
  reason: string | null;
  requiredPlan: string | null;
  subscriptions: SubscriptionItem[];
}

export interface ZombieAlertInsight {
  id: string;
  vendor: string;
  daysSinceUsed: number;
  monthlyCost: string;
  currency: string;
}

export interface RenewalInsight {
  id: string;
  vendor: string;
  daysLeft: number;
  amountUsd: string;
}

export interface PriceIncreaseInsight {
  id: string;
  vendor: string;
  oldAmount: string;
  newAmount: string;
  changePercent: string;
}

export interface WeeklyInsights {
  totalActions: number;
  zombieAlerts: ZombieAlertInsight[];
  renewalsThisWeek: RenewalInsight[];
  priceIncreases: PriceIncreaseInsight[];
}

export interface RoiStats {
  totalSavedUsd: string;
  eventCount: number;
  zombieArchivedCount: number;
}
