import { z } from "zod";

/** Khớp BillingConstants.InvoiceStatus ở backend. */
export const INVOICE_STATUS = {
  unpaid: "UNPAID",
  partiallyPaid: "PARTIALLY_PAID",
  paid: "PAID",
  overdue: "OVERDUE",
  cancelled: "CANCELLED",
} as const;

export type InvoiceStatus = (typeof INVOICE_STATUS)[keyof typeof INVOICE_STATUS];

export const INVOICE_STATUS_LABEL: Record<InvoiceStatus, string> = {
  [INVOICE_STATUS.unpaid]: "Chưa thu",
  [INVOICE_STATUS.partiallyPaid]: "Thu một phần",
  [INVOICE_STATUS.paid]: "Đã thu đủ",
  [INVOICE_STATUS.overdue]: "Quá hạn",
  [INVOICE_STATUS.cancelled]: "Đã huỷ",
};

/** Khớp BillingConstants.PaymentMethod ở backend. */
export const PAYMENT_METHOD = {
  momo: "MOMO",
  vnpay: "VNPAY",
  manual: "MANUAL",
} as const;

export type PaymentMethod = (typeof PAYMENT_METHOD)[keyof typeof PAYMENT_METHOD];

/** Chỉ hai cổng online - MANUAL không gửi được sang endpoint online-payment (backend ném
 * BILLING_UNKNOWN_PAYMENT_GATEWAY), nên kiểu này chặn luôn ở client. */
export type OnlineGateway = typeof PAYMENT_METHOD.momo | typeof PAYMENT_METHOD.vnpay;

export const ONLINE_GATEWAYS: readonly OnlineGateway[] = [PAYMENT_METHOD.momo, PAYMENT_METHOD.vnpay];

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  [PAYMENT_METHOD.momo]: "MoMo",
  [PAYMENT_METHOD.vnpay]: "VNPay",
  [PAYMENT_METHOD.manual]: "Tiền mặt / chuyển khoản",
};

/** Khớp BillingConstants.PaymentStatus ở backend. */
export const PAYMENT_STATUS = {
  pending: "PENDING",
  success: "SUCCESS",
  failed: "FAILED",
} as const;

export type PaymentStatus = (typeof PAYMENT_STATUS)[keyof typeof PAYMENT_STATUS];

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  [PAYMENT_STATUS.pending]: "Đang xử lý",
  [PAYMENT_STATUS.success]: "Thành công",
  [PAYMENT_STATUS.failed]: "Thất bại",
};

/** Khớp BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO - dưới mốc này backend trả
 * BILLING_COMBO_MINIMUM_SIZE, nên form phải chặn trước để người dùng không bấm rồi mới biết. */
export const MIN_COMBO_ENROLLMENTS = 2;

/**
 * Khớp từng field với InvoiceResponse ở backend.
 *
 * Một hoá đơn thuộc về ĐÚNG MỘT trong hai: một ghi danh (enrollmentId + courseId) hoặc một combo
 * (comboId) - backend có CHECK constraint chk_invoices_enrollment_xor_combo bảo đảm điều đó. Ba field
 * này nullable nên mọi nơi hiển thị phải xử lý null, không được .slice() thẳng.
 */
export const invoiceSummarySchema = z.object({
  id: z.string().uuid(),
  enrollmentId: z.string().uuid().nullable(),
  comboId: z.string().uuid().nullable(),
  studentProfileId: z.string().uuid(),
  courseId: z.string().uuid().nullable(),
  branchId: z.string().uuid(),
  installmentNumber: z.number().int(),
  amount: z.number(),
  amountPaid: z.number(),
  status: z.enum([
    INVOICE_STATUS.unpaid,
    INVOICE_STATUS.partiallyPaid,
    INVOICE_STATUS.paid,
    INVOICE_STATUS.overdue,
    INVOICE_STATUS.cancelled,
  ]),
  dueDate: z.string(),
  issuedAt: z.string(),
});

export type InvoiceSummary = z.infer<typeof invoiceSummarySchema>;

/** Khớp từng field với PaymentResponse ở backend - KHÔNG có gatewayTransactionId (payload này đi ra
 * một endpoint public, backend cố ý không trả mã giao dịch). */
export const paymentSchema = z.object({
  id: z.string().uuid(),
  amount: z.number(),
  method: z.enum([PAYMENT_METHOD.momo, PAYMENT_METHOD.vnpay, PAYMENT_METHOD.manual]),
  status: z.enum([PAYMENT_STATUS.pending, PAYMENT_STATUS.success, PAYMENT_STATUS.failed]),
  paidAt: z.string().nullable(),
  createdAt: z.string(),
});

export type Payment = z.infer<typeof paymentSchema>;

/** Khớp InvoiceDetailResponse(invoice, payments) ở backend. */
export const invoiceDetailSchema = z.object({
  invoice: invoiceSummarySchema,
  payments: z.array(paymentSchema),
});

export type InvoiceDetail = z.infer<typeof invoiceDetailSchema>;

/** Khớp InitiateOnlinePaymentResponse(payUrl) ở backend. */
export const payUrlSchema = z.object({
  payUrl: z.string(),
});

export interface CreateInvoicePayload {
  readonly enrollmentId: string;
  readonly amount: number;
  /** ISO date (yyyy-MM-dd) - backend nhận LocalDate. */
  readonly dueDate: string;
}

export interface InitiateOnlinePaymentPayload {
  readonly gateway: OnlineGateway;
}

export interface RecordManualPaymentPayload {
  readonly amount: number;
}
