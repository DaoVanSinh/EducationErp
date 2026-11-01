export const billingKeys = {
  all: ["billing"] as const,
  lists: () => [...billingKeys.all, "invoice", "list"] as const,
  list: (page: number, size: number, enrollmentId?: string, studentProfileId?: string, status?: string) =>
    [...billingKeys.lists(), { page, size, enrollmentId, studentProfileId, status }] as const,
  detail: (invoiceId: string) => [...billingKeys.all, "invoice", "detail", invoiceId] as const,
  comboLists: () => [...billingKeys.all, "combo", "list"] as const,
  comboList: (page: number, size: number, studentProfileId?: string) =>
    [...billingKeys.comboLists(), { page, size, studentProfileId }] as const,
  comboDetail: (comboId: string) => [...billingKeys.all, "combo", "detail", comboId] as const,
  groupLists: () => [...billingKeys.all, "group", "list"] as const,
  groupList: (page: number, size: number, courseId?: string) =>
    [...billingKeys.groupLists(), { page, size, courseId }] as const,
  discountTiers: () => [...billingKeys.all, "combo", "discount-tier", "list"] as const,
  paymentStatus: (gatewayTransactionId: string) =>
    [...billingKeys.all, "payment", "status", gatewayTransactionId] as const,
  vnpayReturn: (signedQuery: string) => [...billingKeys.all, "payment", "vnpay-return", signedQuery] as const,
} as const;
