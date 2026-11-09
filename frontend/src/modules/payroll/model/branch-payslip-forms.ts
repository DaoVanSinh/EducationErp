import { PAYSLIP_PAYMENT_METHOD } from "@/entities/payroll";
import { z } from "zod";

/** Khớp PayrollConstants.Limits.MAX_PAYMENT_NOTE_LENGTH. */
const MAX_PAYMENT_NOTE_LENGTH = 500;

export const markPayslipPaidFormSchema = z.object({
  paidOn: z.string().min(1, "Chọn ngày trả"),
  method: z.enum([PAYSLIP_PAYMENT_METHOD.cash, PAYSLIP_PAYMENT_METHOD.bankTransfer]),
  note: z
    .string()
    .trim()
    .max(MAX_PAYMENT_NOTE_LENGTH, `Ghi chú tối đa ${MAX_PAYMENT_NOTE_LENGTH} ký tự`)
    .transform((value) => (value.length === 0 ? null : value)),
});

export const undoPayslipPaymentFormSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, "Nhập lý do huỷ đánh dấu")
    .max(MAX_PAYMENT_NOTE_LENGTH, `Lý do tối đa ${MAX_PAYMENT_NOTE_LENGTH} ký tự`),
});
