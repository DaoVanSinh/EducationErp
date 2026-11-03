export const teacherKeys = {
  all: ["teacher"] as const,
  lists: () => [...teacherKeys.all, "list"] as const,
  list: (page: number, size: number, branchId?: string) => [...teacherKeys.lists(), { page, size, branchId }] as const,
  details: () => [...teacherKeys.all, "detail"] as const,
  detail: (profileId: string) => [...teacherKeys.details(), profileId] as const,
} as const;
