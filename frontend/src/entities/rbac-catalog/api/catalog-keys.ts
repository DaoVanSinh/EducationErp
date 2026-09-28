export const catalogKeys = {
  all: ["rbac-catalog"] as const,
  detail: () => [...catalogKeys.all, "detail"] as const,
} as const;
