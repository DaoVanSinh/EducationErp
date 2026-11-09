export const studentKeys = {
  all: ["student"] as const,
  lists: () => [...studentKeys.all, "list"] as const,
  list: (page: number, size: number, branchId?: string, query?: string) =>
    [...studentKeys.lists(), { page, size, branchId, query }] as const,
  details: () => [...studentKeys.all, "detail"] as const,
  detail: (profileId: string) => [...studentKeys.details(), profileId] as const,
} as const;
