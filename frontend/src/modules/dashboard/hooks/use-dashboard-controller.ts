import { useSelectedBranch } from "@/entities/branch";
import { useDashboardStats } from "@/modules/dashboard/api/use-dashboard-stats";

export function useDashboardController() {
  const { selectedBranchId } = useSelectedBranch();
  const stats = useDashboardStats(selectedBranchId);

  return {
    stats,
    isPending: stats.isPending,
    isError: stats.isError,
    error: stats.error,
    data: stats.data,
  };
}
