import { z } from "zod";

export const createTeacherFormSchema = z.object({
  accountId: z.string().uuid("Chọn tài khoản"),
  subjects: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? [] : value.split(",").map((s) => s.trim()))),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  bio: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
});

export const updateTeacherFormSchema = z.object({
  subjects: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? [] : value.split(",").map((s) => s.trim()))),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  bio: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  active: z.boolean(),
});
