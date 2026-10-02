export const classKeys = {
  all: ["class"] as const,
  lists: () => [...classKeys.all, "list"] as const,
  list: (page: number, size: number) => [...classKeys.lists(), { page, size }] as const,
} as const;
