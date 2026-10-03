import { z } from "zod";

export const courseSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  standardSessionCount: z.number().int().nullable(),
  /** Học phí toàn khoá, VND. null = khoá học chưa chốt giá (backend: Course.tuitionFee nullable). */
  tuitionFee: z.number().nullable(),
  active: z.boolean(),
});

export type CourseSummary = z.infer<typeof courseSummarySchema>;

export interface CreateCoursePayload {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
}

export interface UpdateCoursePayload {
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
  readonly active: boolean;
}
