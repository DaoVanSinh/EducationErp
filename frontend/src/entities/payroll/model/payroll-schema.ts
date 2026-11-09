import { PAY_TYPE } from "@/shared/constants/pay-type";
import { z } from "zod";

/** Khớp PayrollConstants.PayrollRunStatus ở backend. */
export const PAYROLL_RUN_STATUS = {
  draft: "DRAFT",
  pendingApproval: "PENDING_APPROVAL",
  approved: "APPROVED",
} as const;

export type PayrollRunStatus = (typeof PAYROLL_RUN_STATUS)[keyof typeof PAYROLL_RUN_STATUS];

export const PAYROLL_RUN_STATUS_LABEL: Record<PayrollRunStatus, string> = {
  [PAYROLL_RUN_STATUS.draft]: "Nháp",
  [PAYROLL_RUN_STATUS.pendingApproval]: "Chờ duyệt",
  [PAYROLL_RUN_STATUS.approved]: "Đã duyệt",
};

const payTypeSchema = z.enum([PAY_TYPE.monthly, PAY_TYPE.hourly]);

/** Khớp PayrollConstants.PaymentMethod: lương trả đủ một lần, bằng tiền mặt hoặc chuyển khoản. */
export const PAYSLIP_PAYMENT_METHOD = {
  cash: "CASH",
  bankTransfer: "BANK_TRANSFER",
} as const;

export type PayslipPaymentMethod = (typeof PAYSLIP_PAYMENT_METHOD)[keyof typeof PAYSLIP_PAYMENT_METHOD];

export const PAYSLIP_PAYMENT_METHOD_LABEL: Record<PayslipPaymentMethod, string> = {
  [PAYSLIP_PAYMENT_METHOD.cash]: "Tiền mặt",
  [PAYSLIP_PAYMENT_METHOD.bankTransfer]: "Chuyển khoản",
};

const payslipPaymentMethodSchema = z.enum([PAYSLIP_PAYMENT_METHOD.cash, PAYSLIP_PAYMENT_METHOD.bankTransfer]);

export const payslipSchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  accountFullName: z.string().nullable(),
  payType: payTypeSchema,
  payRate: z.number(),
  sessionCount: z.number().int(),
  hoursWorked: z.number().nullable(),
  paidDays: z.number().int(),
  probationDays: z.number().int(),
  dependentCount: z.number().int(),
  grossPay: z.number(),
  allowancesTotal: z.number(),
  adjustment: z.number(),
  socialInsuranceEmployee: z.number(),
  socialInsuranceEmployer: z.number(),
  incomeTaxWithheld: z.number(),
  netPay: z.number(),
  note: z.string().nullable(),
  /** Chi nhánh chịu trách nhiệm chi trả. Null là giáo viên chưa thuộc chi nhánh nào, admin gốc tự trả. */
  branchId: z.string().uuid().nullable(),
  branchName: z.string().nullable(),
  /** Null là chưa trả. */
  paidOn: z.string().nullable(),
  paymentMethod: payslipPaymentMethodSchema.nullable(),
  /** Ghi chú lúc trả, hoặc lý do khi admin gốc đã huỷ đánh dấu nhầm. */
  paymentNote: z.string().nullable(),
});

export type Payslip = z.infer<typeof payslipSchema>;

/** Một phiếu trong màn Chi trả lương, kèm tháng vì danh sách đi xuyên nhiều kỳ. */
export const branchPayslipSchema = z.object({
  year: z.number().int(),
  month: z.number().int(),
  payslip: payslipSchema,
});

export type BranchPayslip = z.infer<typeof branchPayslipSchema>;

export const branchPayrollSummarySchema = z.object({
  branchId: z.string().uuid().nullable(),
  branchName: z.string().nullable(),
  payslipCount: z.number().int(),
  paidCount: z.number().int(),
  totalNetPay: z.number(),
});

export type BranchPayrollSummary = z.infer<typeof branchPayrollSummarySchema>;

/** Một phiếu lương trong tab "Lương" của Hồ sơ của tôi, chỉ từ kỳ đã duyệt - khớp `payroll.dto.MyPayslipResponse`. */
export const myPayslipSchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  month: z.number().int(),
  periodStart: z.string(),
  periodEnd: z.string(),
  payType: payTypeSchema,
  payRate: z.number(),
  sessionCount: z.number().int(),
  hoursWorked: z.number().nullable(),
  paidDays: z.number().int(),
  probationDays: z.number().int(),
  grossPay: z.number(),
  allowancesTotal: z.number(),
  adjustment: z.number(),
  socialInsuranceEmployee: z.number(),
  incomeTaxWithheld: z.number(),
  netPay: z.number(),
  note: z.string().nullable(),
  /** Null là chờ thanh toán. */
  paidOn: z.string().nullable(),
  paymentMethod: payslipPaymentMethodSchema.nullable(),
});

export type MyPayslip = z.infer<typeof myPayslipSchema>;

export const payrollRunSummarySchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  month: z.number().int(),
  periodStart: z.string(),
  periodEnd: z.string(),
  status: z.enum([PAYROLL_RUN_STATUS.draft, PAYROLL_RUN_STATUS.pendingApproval, PAYROLL_RUN_STATUS.approved]),
  rejectionReason: z.string().nullable(),
  payslipCount: z.number().int(),
  totalGrossPay: z.number(),
  totalNetPay: z.number(),
  totalEmployerInsurance: z.number(),
});

export type PayrollRunSummary = z.infer<typeof payrollRunSummarySchema>;

export const missingPayTermsSchema = z.object({
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  sessionCount: z.number().int(),
});

export type MissingPayTerms = z.infer<typeof missingPayTermsSchema>;

export const payrollRunDetailSchema = z.object({
  run: payrollRunSummarySchema,
  payslips: z.array(payslipSchema),
  missingPayTerms: z.array(missingPayTermsSchema),
  branchSummaries: z.array(branchPayrollSummarySchema),
});

export type PayrollRunDetail = z.infer<typeof payrollRunDetailSchema>;

export interface CreatePayrollRunPayload {
  readonly year: number;
  readonly month: number;
}

/** Phần duy nhất kế toán nhập trên phiếu; giờ dạy và thuế do hệ thống tính. */
export interface UpdatePayslipPayload {
  readonly adjustment: number;
  readonly note: string | null;
}

export interface BranchPayslipFilters {
  readonly branchId?: string;
  readonly year?: number;
  readonly month?: number;
  readonly paid?: boolean;
}

export interface MarkPayslipPaidPayload {
  readonly paidOn: string;
  readonly method: PayslipPaymentMethod;
  readonly note: string | null;
}

export interface UndoPayslipPaymentPayload {
  readonly reason: string;
}

export interface RejectPayrollRunPayload {
  readonly reason: string;
}
