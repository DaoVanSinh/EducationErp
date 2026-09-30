import { dashboardApi } from "@/modules/dashboard/api/dashboard-api";
import { dashboardKeys } from "@/modules/dashboard/api/dashboard-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useDashboardStats() {
  return useQuery({
    queryKey: dashboardKeys.stats(),
    staleTime: QUERY_STALE_TIME_MS.dashboard,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => dashboardApi.getStats(),
  });
}
