import { z } from "zod";

/** Giá trị của `<input type="month">`, dạng "YYYY-MM". */
export const createPayrollRunFormSchema = z
  .object({
    period: z.string().regex(/^\d{4}-\d{2}$/, "Chọn tháng của kỳ lương"),
  })
  .transform(({ period }) => {
    const [year, month] = period.split("-").map(Number);
    return { year: year ?? 0, month: month ?? 0 };
  });

export const adjustPayslipFormSchema = z
  .object({
    adjustment: z.string().trim().refine((value) => value.length > 0 && !Number.isNaN(Number(value)), "Nhập số tiền, nhập 0 nếu không điều chỉnh"),
    note: z.string().trim(),
  })
  .superRefine((values, context) => {
    if (Number(values.adjustment) !== 0 && values.note.length === 0) {
      context.addIssue({ code: "custom", path: ["note"], message: "Nhập lý do của khoản điều chỉnh" });
    }
  })
  .transform((values) => ({
    adjustment: Number(values.adjustment),
    note: values.note.length === 0 ? null : values.note,
  }));

export const rejectPayrollRunFormSchema = z.object({
  reason: z.string().min(1, "Nhập lý do từ chối"),
});
