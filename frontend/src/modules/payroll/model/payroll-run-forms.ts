import { z } from "zod";

export const createPayrollRunFormSchema = z.object({
  year: z.string().min(1, "Nhập năm").transform((value) => Number(value)),
  month: z.string().min(1, "Nhập tháng").transform((value) => Number(value)),
});

export const updatePayslipFormSchema = z.object({
  hoursWorked: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  incomeTaxWithheld: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  note: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
});

export const rejectPayrollRunFormSchema = z.object({
  reason: z.string().min(1, "Nhập lý do từ chối"),
});
