import { allowanceSchema, CONTRACT_TYPE } from "@/entities/payroll";
import { z } from "zod";

export const createContractFormSchema = z.object({
  accountId: z.string().uuid("Chọn tài khoản nhân viên"),
  contractType: z.enum([CONTRACT_TYPE.official, CONTRACT_TYPE.collaborator]),
  baseSalary: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  hourlyRate: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  probationStartDate: z.string().trim().transform((value) => (value.length === 0 ? null : value)),
  probationEndDate: z.string().trim().transform((value) => (value.length === 0 ? null : value)),
  startDate: z.string().min(1, "Chọn ngày bắt đầu"),
  allowances: z.array(allowanceSchema),
});

export const updateContractFormSchema = z.object({
  baseSalary: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  hourlyRate: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  probationEndDate: z.string().trim().transform((value) => (value.length === 0 ? null : value)),
  allowances: z.array(allowanceSchema),
});
