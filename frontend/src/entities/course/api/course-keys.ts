export const courseKeys = {
  all: ["course"] as const,
  lists: () => [...courseKeys.all, "list"] as const,
  list: (page: number, size: number) => [...courseKeys.lists(), { page, size }] as const,
} as const;
