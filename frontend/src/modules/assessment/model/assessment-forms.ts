import { ASSIGNMENT_TYPE } from "@/entities/assessment";
import { z } from "zod";

/** Khớp NUMERIC(5,2) của assignments.max_score ở backend. */
const MAX_SCORE_CEILING = 999.99;
const MAX_TITLE_LENGTH = 255;

/** Form giao/sửa bài. Backend kiểm lại bằng Bean Validation - luật ở đây để người dùng thấy lỗi sớm. */
export const assignmentFormSchema = z.object({
  title: z.string().trim().min(1, "Nhập tên bài").max(MAX_TITLE_LENGTH, "Tên bài quá dài"),
  description: z.string().transform((value) => (value.trim() === "" ? null : value.trim())),
  type: z.enum([ASSIGNMENT_TYPE.homework, ASSIGNMENT_TYPE.miniTest]),
  dueDate: z.string().transform((value) => (value === "" ? null : value)),
  maxScore: z
    .string()
    .min(1, "Nhập thang điểm")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Thang điểm phải lớn hơn 0")
    .refine((value) => value <= MAX_SCORE_CEILING, `Thang điểm tối đa ${MAX_SCORE_CEILING}`),
  active: z.boolean(),
});

export type AssignmentFormInput = z.input<typeof assignmentFormSchema>;
