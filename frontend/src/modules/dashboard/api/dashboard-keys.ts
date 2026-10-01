export const dashboardKeys = {
  all: ["dashboard"] as const,
  stats: (branchId: string | null) => [...dashboardKeys.all, "stats", { branchId }] as const,
} as const;
