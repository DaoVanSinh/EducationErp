export const teacherKeys = {
  all: ["teacher"] as const,
  lists: () => [...teacherKeys.all, "list"] as const,
  list: (page: number, size: number, branchId?: string, query?: string) =>
    [...teacherKeys.lists(), { page, size, branchId, query }] as const,
  details: () => [...teacherKeys.all, "detail"] as const,
  detail: (profileId: string) => [...teacherKeys.details(), profileId] as const,
  pay: (profileId: string) => [...teacherKeys.all, "pay", profileId] as const,
  branchHistory: (profileId: string) => [...teacherKeys.all, "branch-history", profileId] as const,
} as const;
