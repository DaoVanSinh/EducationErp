export const classKeys = {
  all: ["class"] as const,
  lists: () => [...classKeys.all, "list"] as const,
  list: (page: number, size: number, teacherId?: string) =>
    [...classKeys.lists(), { page, size, teacherId }] as const,
} as const;
