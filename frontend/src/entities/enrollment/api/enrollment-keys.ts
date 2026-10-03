export const enrollmentKeys = {
  all: ["enrollment"] as const,
  lists: () => [...enrollmentKeys.all, "list"] as const,
  list: (page: number, size: number, studentProfileId?: string, classId?: string) =>
    [...enrollmentKeys.lists(), { page, size, studentProfileId, classId }] as const,
  detail: (enrollmentId: string) => [...enrollmentKeys.all, "detail", enrollmentId] as const,
} as const;
