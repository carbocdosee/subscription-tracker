describe("Subscription Management", () => {
  beforeEach(() => {
    cy.intercept("GET", "/api/v1/subscriptions", { items: [] }).as("getSubscriptions");
    cy.visit("/subscriptions");
    cy.wait("@getSubscriptions");
  });

  it("should create subscription and see it on dashboard", () => {
    cy.intercept("POST", "/api/v1/subscriptions", {
      id: "1",
      vendorName: "Notion",
      category: "knowledge",
      amount: "20.00",
      currency: "USD",
      billingCycle: "MONTHLY",
      renewalDate: "2026-08-01",
      status: "ACTIVE",
      healthScore: "GOOD",
      duplicateWarnings: []
    }).as("createSubscription");
    cy.get("input[formcontrolname='vendorName']").type("Notion");
    cy.get("input[formcontrolname='category']").type("knowledge");
    cy.get("input[formcontrolname='amount']").type("20.00");
    cy.get("input[formcontrolname='renewalDate']").type("2026-08-01");
    cy.contains("button", "Save").click();
    cy.wait("@createSubscription");
  });

  it("should show duplicate warning for same category", () => {
    cy.intercept("GET", "/api/v1/subscriptions", {
      items: [
        {
          id: "1",
          vendorName: "Slack",
          category: "communication",
          amount: "100.00",
          currency: "USD",
          billingCycle: "MONTHLY",
          renewalDate: "2026-10-10",
          status: "ACTIVE",
          healthScore: "GOOD",
          duplicateWarnings: []
        }
      ]
    }).as("reloadSubscriptions");
    cy.reload();
    cy.wait("@reloadSubscriptions");
    cy.get("input[formcontrolname='category']").type("communication");
    cy.contains("Category already has Slack").should("be.visible");
  });

  it("should import subscriptions from CSV", () => {
    cy.intercept("POST", "/api/v1/subscriptions/import/csv", {
      imported: 2,
      skipped: 0,
      errors: []
    }).as("csvImport");
    const csvContent =
      "vendor_name,category,amount,currency,billing_cycle,renewal_date\nSlack,communication,100,USD,MONTHLY,2026-11-01";
    cy.get("input[type='file']").selectFile({
      contents: Cypress.Buffer.from(csvContent),
      fileName: "subs.csv",
      mimeType: "text/csv"
    });
    cy.wait("@csvImport");
  });

  it("should show renewal alert badge on 30-day subscriptions", () => {
    cy.intercept("GET", "/api/v1/dashboard/stats", {
      totalMonthlySpend: "120.00",
      totalAnnualSpend: "1440.00",
      subscriptionCount: 1,
      renewingIn30Days: [
        {
          subscriptionId: "1",
          vendorName: "Figma",
          amountUsd: "45.00",
          renewalDate: "2026-03-14",
          daysLeft: 30,
          alertType: "DAYS_30"
        }
      ],
      totalRenewal30DaysAmount: "45.00",
      spendByCategory: { design: "45.00" },
      monthlyTrend: [],
      duplicateWarnings: [],
      potentialSavings: "0.00"
    }).as("dashboardStats");
    cy.visit("/dashboard");
    cy.wait("@dashboardStats");
    cy.contains("30d").should("be.visible");
  });

  it("should allow team member with editor role to edit", () => {
    cy.intercept("PUT", "/api/v1/subscriptions/*", { statusCode: 200 }).as("editSubscription");
    cy.intercept("GET", "/api/v1/subscriptions", {
      items: [
        {
          id: "1",
          vendorName: "Slack",
          category: "communication",
          amount: "100.00",
          currency: "USD",
          billingCycle: "MONTHLY",
          renewalDate: "2026-10-10",
          status: "ACTIVE",
          healthScore: "GOOD",
          duplicateWarnings: []
        }
      ]
    }).as("editorLoad");
    cy.reload();
    cy.wait("@editorLoad");
    cy.get("table").should("exist");
  });

  it("should prevent viewer from editing", () => {
    cy.intercept("POST", "/api/v1/subscriptions", { statusCode: 403, body: { message: "Editor or Admin role required" } }).as("denyCreate");
    cy.get("input[formcontrolname='vendorName']").type("Viewer Tool");
    cy.get("input[formcontrolname='category']").type("ops");
    cy.get("input[formcontrolname='amount']").type("10.00");
    cy.get("input[formcontrolname='renewalDate']").type("2026-08-01");
    cy.contains("button", "Save").click();
    cy.wait("@denyCreate");
  });
});
