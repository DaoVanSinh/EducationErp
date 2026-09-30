import { useDashboardStats } from "@/modules/dashboard/api/use-dashboard-stats";

export function useDashboardController() {
  const stats = useDashboardStats();

  return {
    stats,
    isPending: stats.isPending,
    isError: stats.isError,
    error: stats.error,
    data: stats.data,
  };
}
