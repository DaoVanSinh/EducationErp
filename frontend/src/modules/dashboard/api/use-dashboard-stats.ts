import { dashboardApi } from "@/modules/dashboard/api/dashboard-api";
import { dashboardKeys } from "@/modules/dashboard/api/dashboard-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useDashboardStats(branchId: string | null) {
  return useQuery({
    queryKey: dashboardKeys.stats(branchId),
    staleTime: QUERY_STALE_TIME_MS.dashboard,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => dashboardApi.getStats(branchId),
  });
}
