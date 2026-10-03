export const billingKeys = {
  all: ["billing"] as const,
  lists: () => [...billingKeys.all, "invoice", "list"] as const,
  list: (page: number, size: number, enrollmentId?: string, studentProfileId?: string, status?: string) =>
    [...billingKeys.lists(), { page, size, enrollmentId, studentProfileId, status }] as const,
  detail: (invoiceId: string) => [...billingKeys.all, "invoice", "detail", invoiceId] as const,
  paymentStatus: (gatewayTransactionId: string) =>
    [...billingKeys.all, "payment", "status", gatewayTransactionId] as const,
} as const;
