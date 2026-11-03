export const landingKeys = {
  all: ["landing"] as const,
  overview: () => [...landingKeys.all, "overview"] as const,
  adminConfig: () => [...landingKeys.all, "admin-config"] as const,
  consultations: () => [...landingKeys.all, "consultations"] as const,
  consultationList: (branchId?: string) => [...landingKeys.consultations(), { branchId }] as const,
} as const;
