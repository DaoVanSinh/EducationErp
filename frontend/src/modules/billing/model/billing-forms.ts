import { MIN_COMBO_ENROLLMENTS } from "@/entities/billing";
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

export const createComboFormSchema = z.object({
  studentProfileId: z.string().uuid("Chọn học viên"),
  enrollmentIds: z
    .array(z.string().uuid())
    .min(MIN_COMBO_ENROLLMENTS, `Chọn ít nhất ${MIN_COMBO_ENROLLMENTS} khoá đang học`),
  dueDate: z.string().min(1, "Chọn hạn đóng của combo"),
});

export const comboInvoiceFormSchema = z.object({
  amount: z
    .string()
    .min(1, "Nhập số tiền")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Số tiền phải lớn hơn 0"),
  dueDate: z.string().min(1, "Chọn hạn thanh toán của đợt này"),
});

export const comboDiscountTierFormSchema = z.object({
  minCourseCount: z
    .string()
    .min(1, "Nhập số khoá tối thiểu")
    .transform((value) => Number(value))
    .refine(
      (value) => Number.isInteger(value) && value >= MIN_COMBO_ENROLLMENTS,
      `Số khoá tối thiểu phải từ ${MIN_COMBO_ENROLLMENTS}`,
    ),
  discountPercent: z
    .string()
    .min(1, "Nhập % giảm")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value >= 0 && value <= 100, "% giảm phải trong 0-100"),
});
