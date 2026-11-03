export const courseKeys = {
  all: ["course"] as const,
  lists: () => [...courseKeys.all, "list"] as const,
  list: (page: number, size: number, branchId?: string) => [...courseKeys.lists(), { page, size, branchId }] as const,
} as const;
