import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { z } from "zod";

/** Khớp từng field với EnrollmentResponse ở backend - lệch tên là parse() ném ngay tại biên. */
export const enrollmentSummarySchema = z.object({
  id: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  classId: z.string().uuid(),
  courseId: z.string().uuid(),
  branchId: z.string().uuid(),
  status: z.enum([ENROLLMENT_STATUS.active, ENROLLMENT_STATUS.withdrawn, ENROLLMENT_STATUS.completed]),
  enrolledAt: z.string(),
  withdrawnAt: z.string().nullable(),
});

export type EnrollmentSummary = z.infer<typeof enrollmentSummarySchema>;

export interface CreateEnrollmentPayload {
  readonly studentProfileId: string;
  readonly classId: string;
}
