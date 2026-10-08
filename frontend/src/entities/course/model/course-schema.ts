import { z } from "zod";
import { gradeColumnSchema, type GradeColumnSlot } from "@/shared/api/schemas";

export const courseSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  standardSessionCount: z.number().int().nullable(),
  /** Học phí toàn khoá, VND. null = khoá học chưa chốt giá (backend: Course.tuitionFee nullable). */
  tuitionFee: z.number().nullable(),
  active: z.boolean(),
  /** Rỗng = khóa học chưa cấu hình cột điểm (hợp lệ). Tổng weightPercent của danh sách không rỗng
   * luôn là 100 vì backend chặn lưu nếu khác. */
  gradeColumns: z.array(gradeColumnSchema),
});

export type CourseSummary = z.infer<typeof courseSummarySchema>;

export interface CreateCoursePayload {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
  readonly gradeColumns: readonly GradeColumnSlot[];
}

export interface UpdateCoursePayload {
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
  readonly active: boolean;
  readonly gradeColumns: readonly GradeColumnSlot[];
}
