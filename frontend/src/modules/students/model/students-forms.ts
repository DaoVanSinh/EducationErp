import { z } from "zod";

export const createStudentFormSchema = z.object({
  accountId: z.string().uuid("Chọn tài khoản"),
  dateOfBirth: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  sourceChannel: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
});

export const updateStudentFormSchema = z.object({
  dateOfBirth: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  sourceChannel: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  active: z.boolean(),
});
