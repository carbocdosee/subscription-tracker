import { inject } from "@angular/core";
import { patchState, signalStore, withMethods, withState } from "@ngrx/signals";
import { firstValueFrom } from "rxjs";
import { SubscriptionItem } from "../../shared/models";
import { TrackerApiService } from "./tracker-api.service";

type SubscriptionState = {
  loading: boolean;
  error: string | null;
  items: SubscriptionItem[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
  vendorFilter: string;
  categoryFilter: string;
  statusFilter: string;
  zombieFilter: boolean | null;
};

const initialState: SubscriptionState = {
  loading: false,
  error: null,
  items: [],
  total: 0,
  page: 1,
  size: 25,
  totalPages: 0,
  vendorFilter: "",
  categoryFilter: "",
  statusFilter: "",
  zombieFilter: null
};

export const SubscriptionStore = signalStore(
  { providedIn: "root" },
  withState(initialState),
  withMethods((store, api = inject(TrackerApiService)) => ({
    async load(params?: { page?: number; size?: number; vendor?: string; category?: string; status?: string; zombie?: boolean | null }) {
      const update: Partial<SubscriptionState> = { loading: true, error: null };
      if (params?.page != null) update.page = params.page;
      if (params?.size != null) update.size = params.size;
      if (params?.vendor != null) update.vendorFilter = params.vendor;
      if (params?.category != null) update.categoryFilter = params.category;
      if (params?.status != null) update.statusFilter = params.status;
      if ("zombie" in (params ?? {})) update.zombieFilter = params!.zombie ?? null;
      patchState(store, update);

      try {
        const response = await firstValueFrom(
          api.getSubscriptions({
            page: store.page(),
            size: store.size(),
            vendor: store.vendorFilter() || undefined,
            category: store.categoryFilter() || undefined,
            status: store.statusFilter() || undefined,
            zombie: store.zombieFilter() ?? undefined
          })
        );
        patchState(store, {
          items: response.items,
          total: response.total,
          totalPages: response.totalPages,
          loading: false
        });
      } catch (error: unknown) {
        patchState(store, { loading: false, error: "Unable to load subscriptions" });
      }
    },
    async remove(id: string) {
      try {
        await firstValueFrom(api.deleteSubscription(id));
        patchState(store, { items: store.items().filter((item) => item.id !== id) });
      } catch {
        patchState(store, { error: "Unable to delete subscription" });
      }
    },
    async markPaid(id: string) {
      try {
        const updated = await firstValueFrom(api.markSubscriptionPaid(id));
        patchState(store, {
          items: store.items().map((item) => (item.id === id ? updated : item)),
          error: null
        });
      } catch {
        patchState(store, { error: "Unable to mark payment as paid" });
      }
    },
    async update(id: string, payload: Record<string, unknown>): Promise<SubscriptionItem> {
      const updated = await firstValueFrom(api.updateSubscription(id, payload));
      patchState(store, {
        items: store.items().map((item) => (item.id === id ? updated : item)),
        error: null
      });
      return updated;
    },
    async markUsed(id: string): Promise<SubscriptionItem> {
      const updated = await firstValueFrom(api.markSubscriptionUsed(id));
      patchState(store, {
        items: store.items().map((item) => (item.id === id ? updated : item)),
        error: null
      });
      return updated;
    }
  }))
);
