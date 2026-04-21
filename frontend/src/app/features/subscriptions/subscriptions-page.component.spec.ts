import { render, screen } from "@testing-library/angular";
import { of } from "rxjs";
import { SubscriptionsPageComponent } from "./subscriptions-page.component";
import { TrackerApiService } from "../../core/services/tracker-api.service";
import { SubscriptionStore } from "../../core/services/subscription.store";
import { ConfirmationService } from "primeng/api";

describe("SubscriptionsPageComponent", () => {
  it("renders subscriptions title", async () => {
    await render(SubscriptionsPageComponent, {
      providers: [
        ConfirmationService,
        {
          provide: TrackerApiService,
          useValue: {
            getSubscriptions: () => of({ items: [] }),
            createSubscription: () => of({}),
            importCsv: () => of({ imported: 0, skipped: 0, errors: [] }),
            getCategories: () => of({ predefined: [], custom: [] }),
            getMembers: () => of({ items: [] })
          }
        },
        {
          provide: SubscriptionStore,
          useValue: {
            items: () => [],
            total: () => 0,
            page: () => 1,
            size: () => 25,
            totalPages: () => 0,
            loading: () => false,
            error: () => null,
            vendorFilter: () => "",
            categoryFilter: () => "",
            statusFilter: () => "",
            load: () => Promise.resolve(),
            remove: () => Promise.resolve(),
            markPaid: () => Promise.resolve()
          }
        }
      ]
    });

    if (!screen.queryByText("Subscriptions")) {
      throw new Error("Subscriptions title is not rendered");
    }
  });
});
