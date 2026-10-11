export const studentKeys = {
  all: ["student"] as const,
  lists: () => [...studentKeys.all, "list"] as const,
  list: (page: number, size: number) => [...studentKeys.lists(), { page, size }] as const,
  details: () => [...studentKeys.all, "detail"] as const,
  detail: (profileId: string) => [...studentKeys.details(), profileId] as const,
} as const;
