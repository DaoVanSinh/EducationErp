import { z } from "zod";

/** Khớp EnrollmentConstants.EnrollmentStatus ở backend. */
export const ENROLLMENT_STATUS = {
  active: "ACTIVE",
  withdrawn: "WITHDRAWN",
  completed: "COMPLETED",
} as const;

export type EnrollmentStatus = (typeof ENROLLMENT_STATUS)[keyof typeof ENROLLMENT_STATUS];

export const ENROLLMENT_STATUS_LABEL: Record<EnrollmentStatus, string> = {
  [ENROLLMENT_STATUS.active]: "Đang học",
  [ENROLLMENT_STATUS.withdrawn]: "Đã rút",
  [ENROLLMENT_STATUS.completed]: "Đã hoàn tất",
};

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
