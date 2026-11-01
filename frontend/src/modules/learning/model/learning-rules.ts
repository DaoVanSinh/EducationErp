import { INVOICE_STATUS, type InvoiceDetail, type InvoiceStatus } from "@/entities/billing";
import { ENROLLMENT_STATUS, type EnrollmentStatus } from "@/shared/api/schemas";

/** Nhánh hiển thị tách khỏi JSX (Mandate #3) - giữ component thuần render. */
type Tone = "neutral" | "positive" | "warning" | "danger";

const INVOICE_TONE: Record<InvoiceStatus, Tone> = {
  [INVOICE_STATUS.unpaid]: "warning",
  [INVOICE_STATUS.partiallyPaid]: "warning",
  [INVOICE_STATUS.paid]: "positive",
  [INVOICE_STATUS.overdue]: "danger",
  [INVOICE_STATUS.cancelled]: "neutral",
};

const ENROLLMENT_TONE: Record<EnrollmentStatus, Tone> = {
  [ENROLLMENT_STATUS.active]: "positive",
  [ENROLLMENT_STATUS.withdrawn]: "neutral",
  [ENROLLMENT_STATUS.completed]: "neutral",
};

export function invoiceTone(status: InvoiceStatus): Tone {
  return INVOICE_TONE[status];
}

export function enrollmentTone(status: EnrollmentStatus): Tone {
  return ENROLLMENT_TONE[status];
}

export interface TuitionTotals {
  readonly total: number;
  readonly paid: number;
  readonly remaining: number;
}

/** Khớp BillingRules.isPayable ở backend: chưa thu, thu một phần, quá hạn vẫn còn nhận tiền. */
const PAYABLE_STATUSES: ReadonlySet<InvoiceStatus> = new Set([
  INVOICE_STATUS.unpaid,
  INVOICE_STATUS.partiallyPaid,
  INVOICE_STATUS.overdue,
]);

/** Học viên chỉ thấy nút thanh toán online khi hoá đơn còn nhận tiền và còn nợ. */
export function canPayOnline(invoice: InvoiceDetail["invoice"]): boolean {
  return PAYABLE_STATUSES.has(invoice.status) && invoice.amount > invoice.amountPaid;
}

/** Tổng học phí phải đóng / đã đóng / còn lại - hoá đơn đã huỷ không tính. */
export function tuitionTotals(invoices: readonly InvoiceDetail[]): TuitionTotals {
  const billable = invoices.filter((detail) => detail.invoice.status !== INVOICE_STATUS.cancelled);
  const total = billable.reduce((sum, detail) => sum + detail.invoice.amount, 0);
  const paid = billable.reduce((sum, detail) => sum + detail.invoice.amountPaid, 0);
  return { total, paid, remaining: Math.max(total - paid, 0) };
}
