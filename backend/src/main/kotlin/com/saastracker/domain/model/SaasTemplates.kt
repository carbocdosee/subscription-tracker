package com.saastracker.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SaasTemplate(
    val id: String,
    val name: String,
    val category: String,
    val defaultBillingCycle: BillingCycle,
    val defaultAmountUsd: Double,
    val logoUrl: String,
    val websiteUrl: String
)

val SAAS_TEMPLATES: List<SaasTemplate> = listOf(
    // Design
    SaasTemplate("figma",      "Figma",            "Design",        BillingCycle.MONTHLY, 12.0,   "https://logo.clearbit.com/figma.com",            "https://figma.com"),
    SaasTemplate("canva",      "Canva",             "Design",        BillingCycle.MONTHLY, 12.99,  "https://logo.clearbit.com/canva.com",            "https://canva.com"),
    // Productivity
    SaasTemplate("notion",     "Notion",            "Productivity",  BillingCycle.MONTHLY, 8.0,    "https://logo.clearbit.com/notion.so",            "https://notion.so"),
    SaasTemplate("miro",       "Miro",              "Productivity",  BillingCycle.MONTHLY, 8.0,    "https://logo.clearbit.com/miro.com",             "https://miro.com"),
    SaasTemplate("loom",       "Loom",              "Productivity",  BillingCycle.MONTHLY, 8.0,    "https://logo.clearbit.com/loom.com",             "https://loom.com"),
    SaasTemplate("gdrive",     "Google Workspace",  "Productivity",  BillingCycle.MONTHLY, 6.0,    "https://logo.clearbit.com/google.com",           "https://workspace.google.com"),
    // Communication
    SaasTemplate("slack",      "Slack",             "Communication", BillingCycle.MONTHLY, 7.25,   "https://logo.clearbit.com/slack.com",            "https://slack.com"),
    SaasTemplate("zoom",       "Zoom",              "Communication", BillingCycle.MONTHLY, 14.99,  "https://logo.clearbit.com/zoom.us",              "https://zoom.us"),
    SaasTemplate("lark",       "Lark",              "Communication", BillingCycle.MONTHLY, 12.0,   "https://logo.clearbit.com/larksuite.com",        "https://larksuite.com"),
    // Development
    SaasTemplate("github",     "GitHub",            "Development",   BillingCycle.MONTHLY, 4.0,    "https://logo.clearbit.com/github.com",           "https://github.com"),
    SaasTemplate("gitlab",     "GitLab",            "Development",   BillingCycle.MONTHLY, 19.0,   "https://logo.clearbit.com/gitlab.com",           "https://gitlab.com"),
    SaasTemplate("jira",       "Jira",              "Development",   BillingCycle.MONTHLY, 8.15,   "https://logo.clearbit.com/atlassian.com",        "https://atlassian.com/jira"),
    SaasTemplate("linear",     "Linear",            "Development",   BillingCycle.MONTHLY, 8.0,    "https://logo.clearbit.com/linear.app",           "https://linear.app"),
    SaasTemplate("sentry",     "Sentry",            "Development",   BillingCycle.MONTHLY, 26.0,   "https://logo.clearbit.com/sentry.io",            "https://sentry.io"),
    SaasTemplate("datadog",    "Datadog",           "Development",   BillingCycle.MONTHLY, 15.0,   "https://logo.clearbit.com/datadoghq.com",        "https://datadoghq.com"),
    SaasTemplate("vercel",     "Vercel",            "Development",   BillingCycle.MONTHLY, 20.0,   "https://logo.clearbit.com/vercel.com",           "https://vercel.com"),
    // Project Management
    SaasTemplate("asana",      "Asana",             "Project Mgmt",  BillingCycle.MONTHLY, 10.99,  "https://logo.clearbit.com/asana.com",            "https://asana.com"),
    SaasTemplate("monday",     "Monday.com",        "Project Mgmt",  BillingCycle.MONTHLY, 9.0,    "https://logo.clearbit.com/monday.com",           "https://monday.com"),
    SaasTemplate("clickup",    "ClickUp",           "Project Mgmt",  BillingCycle.MONTHLY, 7.0,    "https://logo.clearbit.com/clickup.com",          "https://clickup.com"),
    SaasTemplate("trello",     "Trello",            "Project Mgmt",  BillingCycle.MONTHLY, 5.0,    "https://logo.clearbit.com/trello.com",           "https://trello.com"),
    // CRM & Sales
    SaasTemplate("hubspot",    "HubSpot",           "CRM",           BillingCycle.MONTHLY, 20.0,   "https://logo.clearbit.com/hubspot.com",          "https://hubspot.com"),
    SaasTemplate("pipedrive",  "Pipedrive",         "CRM",           BillingCycle.MONTHLY, 14.9,   "https://logo.clearbit.com/pipedrive.com",        "https://pipedrive.com"),
    SaasTemplate("salesforce", "Salesforce",        "CRM",           BillingCycle.MONTHLY, 25.0,   "https://logo.clearbit.com/salesforce.com",       "https://salesforce.com"),
    // Marketing
    SaasTemplate("mailchimp",  "Mailchimp",         "Marketing",     BillingCycle.MONTHLY, 13.0,   "https://logo.clearbit.com/mailchimp.com",        "https://mailchimp.com"),
    SaasTemplate("ahrefs",     "Ahrefs",            "Marketing",     BillingCycle.MONTHLY, 99.0,   "https://logo.clearbit.com/ahrefs.com",           "https://ahrefs.com"),
    // HR
    SaasTemplate("gusto",      "Gusto",             "HR",            BillingCycle.MONTHLY, 40.0,   "https://logo.clearbit.com/gusto.com",            "https://gusto.com"),
    // Finance
    SaasTemplate("quickbooks", "QuickBooks",        "Finance",       BillingCycle.MONTHLY, 30.0,   "https://logo.clearbit.com/quickbooks.intuit.com","https://quickbooks.intuit.com"),
    // Storage
    SaasTemplate("dropbox",    "Dropbox",           "Storage",       BillingCycle.MONTHLY, 9.99,   "https://logo.clearbit.com/dropbox.com",          "https://dropbox.com"),
    // Automation
    SaasTemplate("zapier",     "Zapier",            "Automation",    BillingCycle.MONTHLY, 19.99,  "https://logo.clearbit.com/zapier.com",           "https://zapier.com"),
    SaasTemplate("make",       "Make",              "Automation",    BillingCycle.MONTHLY, 9.0,    "https://logo.clearbit.com/make.com",             "https://make.com"),
)
