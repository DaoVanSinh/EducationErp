import { dashboardStatsSchema } from "@/modules/dashboard/model/dashboard-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const dashboardApi = {
  async getStats() {
    return dashboardStatsSchema.parse(await apiClient.get<unknown>(API_ROUTE.dashboard.stats));
  },
} as const;
