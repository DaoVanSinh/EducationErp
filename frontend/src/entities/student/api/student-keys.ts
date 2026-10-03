export const studentKeys = {
  all: ["student"] as const,
  lists: () => [...studentKeys.all, "list"] as const,
  list: (page: number, size: number) => [...studentKeys.lists(), { page, size }] as const,
} as const;
