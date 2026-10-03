import { z } from "zod";

export const createInvoiceFormSchema = z.object({
  enrollmentId: z.string().uuid("Chọn ghi danh"),
  amount: z
    .string()
    .min(1, "Nhập số tiền")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Số tiền phải lớn hơn 0"),
  dueDate: z.string().min(1, "Chọn hạn thanh toán"),
});

export const manualPaymentFormSchema = z.object({
  amount: z
    .string()
    .min(1, "Nhập số tiền")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Số tiền phải lớn hơn 0"),
});
